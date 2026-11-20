<<<<<<< HEAD
# STDISCM Distributed Enrollment System: Running the App

## Step 1: Generate the JWT keys (first time only)

`keys/` and `view/.env` are gitignored, so **you won't have them after cloning**. From the repo root, in Git Bash:

```
bash docker/gen-keys.sh
```

This creates `keys/private.pem` and `keys/public.pem`. The View needs the public key to verify logins. Mock login needs the private key to sign tokens.

## Step 2: Create `view/.env` (first time only)

Create the file `view/.env`. For a quick start, use this (mock mode, see Step 5 for the other presets):

```
USE_MOCKS=true
DEAD_NODES=
```

## Step 3: Start MySQL (real-nodes mode only)

Skip Steps 3 and 4 if you only want mock mode.

From the repo root, in PowerShell:

```
docker run -d --name mysql -e MYSQL_ROOT_PASSWORD=root -p 3306:3306 -v "${PWD}/docker/init:/docker-entrypoint-initdb.d" mysql:8
```

This runs `docker/init/02-course-db.sql` and `03-grades-db.sql`, which create `course_db`, `grades_db`, the tables, demo data and the service DB users. Wait about 30 seconds, then check:

```
docker logs mysql
```

Wait for "ready for connections". You will see it **twice**, because MySQL restarts itself once after the init scripts.

- The init scripts only run when the container is **first created**. To reset the data: `docker rm -f mysql`, then run the command again.
- **"port 3306 is already in use"?** You probably have MySQL installed locally. Either stop it, or run the container on another port by using `-p 3307:3306` instead. If you do that, every node terminal in Step 4 needs `DB_URL` pointing to that port (see the note there).
- If a container named `mysql` already exists, run `docker rm mysql` first. Use `docker start mysql` to start an existing one.

## Step 4: Build and start the Java nodes (real-nodes mode only)

Build once (and again after pulling new Java code):

```
mvn -f java/pom.xml package -DskipTests
```

You should see `BUILD SUCCESS`. Then open **four separate PowerShell terminals** in the repo root, one per node. Each should keep running and not return to a prompt.

```
java -jar java/catalog-service/target/catalog-service-1.0.0-SNAPSHOT.jar
```
```
java -jar java/enrollment-service/target/enrollment-service-1.0.0-SNAPSHOT.jar
```
```
java -jar java/grades-view-service/target/grades-view-service-1.0.0-SNAPSHOT.jar
```
```
java -jar java/grades-upload-service/target/grades-upload-service-1.0.0-SNAPSHOT.jar
```

The default ports and DB credentials already match MySQL on `localhost:3306`, so no env vars are needed.

**If your MySQL is on 3307**, set `DB_URL` in each terminal before its `java -jar` line:

```
# catalog and enrollment
$env:DB_URL="jdbc:mysql://localhost:3307/course_db"

# grades view and grades upload
$env:DB_URL="jdbc:mysql://localhost:3307/grades_db"
```

If a node crashes with "Public Key Retrieval is not allowed", add `?allowPublicKeyRetrieval=true` to the end of its `DB_URL`. If it crashes right at startup with a connection error, MySQL probably wasn't ready yet, so wait and run it again.

## Step 5: Switch between mock mode and real-nodes mode

The View decides what to talk to using `view/.env`. Pick one of these presets, **save the file, then stop the View (Ctrl+C) and start it again with `npm run dev`**. The settings are only read at startup.

| Mode | `view/.env` | What you need running |
| --- | --- | --- |
| **Mock** (everything fake) | `USE_MOCKS=true`<br>`DEAD_NODES=` | Just the View |
| **Real nodes + mock login** (use this now) | `USE_MOCKS=false`<br>`MOCK_SERVICES=auth`<br>`DEAD_NODES=` | MySQL + the 4 Java nodes + View |
| **Fully real** (once Auth exists) | `USE_MOCKS=false`<br>`DEAD_NODES=` | MySQL + all 5 nodes + View |
| **Mixed** (fake only some nodes) | `USE_MOCKS=false`<br>`MOCK_SERVICES=auth,catalog`<br>`DEAD_NODES=` | Real nodes for everything not listed |

Service names for `MOCK_SERVICES` and `DEAD_NODES` are `auth`, `catalog`, `enrollment`, `gradesView` and `gradesUpload` (comma-separated, case-sensitive).

**How to tell which mode you're in:** the mock `/courses` page shows only 3 courses (CSADPRG, STDISCM, STADVDB). The real database has 8 courses, including CCPROG3, CCINOV8 and CSNETWK.

## Step 6: Start the View

```
cd view
npm install
npm run dev
```

Open http://localhost:8080.

Use `npm run dev`, **not** `npm start`. Only `dev` loads `.env`; `npm start` ignores it and tries to reach real nodes.

### Demo logins (mock login only)

All accounts use the password `password123`.

- Students: `12012345`, `12012346`, `12012347`, `12012348`, `12012349`
- Faculty: `fac001`, `fac002`, `fac003`

---

## Checking that everything is connected (real-nodes mode)

Log in and click through each page. If it shows real data, that link works:

- `/courses` shows 8 courses from the database
- `/enroll` and `/enrollments` use the enrollment node
- `/grades` uses the grades view node (log in as `12012345` and you should see CCPROG3 and STADVDB)
- `/grades/upload` (faculty) uses the grades upload node

A page that says "currently unavailable" means the View can't reach that node. Check that its terminal is still running and on the right port.

## Testing node failures

**Real nodes:**

1. Log in as a student and a faculty member.
2. Close one node's terminal (Ctrl+C).
3. Click every page. Only that node's pages should show the 503 "currently unavailable" message (it can take up to 2 seconds). Everything else should work.
4. Start the node again and refresh. The page should recover **without restarting the View or logging in again**.

| Node stopped | What should fail | What should still work |
| --- | --- | --- |
| Catalog | `/courses` | enrolling, my enrollments, grades |
| Enrollment | `/enroll`, `/enrollments` | courses, grades |
| Grades View | `/grades` | faculty upload, courses, enrollment |
| Grades Upload | `/grades/upload` | student grades, courses, enrollment |
| Auth | new logins only | everything for users already logged in (the View checks tokens locally) |

Also try starting the View while one node was never started: the rest should boot and work.

**Mock mode:** set `DEAD_NODES=catalog` (or any list of names) in `.env` and restart the View to fake a dead node. This is handy for checking the error pages, but it does not test real recovery.

`docs/member4-demo.md` has a table for recording these results.

---

## Looking inside the database

```
docker exec -it mysql mysql -uroot -proot
```

Then:

```sql
SHOW DATABASES;
USE course_db;
SELECT * FROM courses;
SELECT * FROM enrollments;   -- fills up as students enroll
USE grades_db;
SELECT * FROM grades;        -- fills up as faculty upload grades
exit
```

Or run a single query without opening the prompt:

```
docker exec -it mysql mysql -uroot -proot -e "SELECT * FROM course_db.enrollments;"
```

The root password is `root`. The service accounts (`catalog_user`, `enrollment_user`, `grades_view_user`, `grades_upload_user`) are created by the init scripts and used by the nodes automatically.
=======
## Authentication demo accounts

The local demo database seeds these accounts. The password for every account is
`password123`

the database stores only per-account BCrypt hashes.

| Username | Role | Demo password |
| --- | --- | --- |
| `12012345` | Student | `password123` |
| `12012346` | Student | `password123` |
| `12012347` | Student | `password123` |
| `12012348` | Student | `password123` |
| `12012349` | Student | `password123` |
| `fac001` | Faculty | `password123` |
| `fac002` | Faculty | `password123` |
| `fac003` | Faculty | `password123` |

## Run the full system with Docker Compose

Requirements: Docker Desktop with Compose. From the repository root, create a
local environment file:

```powershell
Copy-Item .env.example .env
```

The Compose stack uses `keys/private.pem` and `keys/public.pem`. If either key
is missing, generate the pair with `bash docker/gen-keys.sh` (for example from
Git Bash). That script overwrites an existing pair, so do not run it when you
need to preserve the current signing key.

Start all seven containers from the repository root:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up --build
```

The frontend is available at <http://localhost:8080>; MySQL is published on
port `3307`, and the gRPC services on ports `50051` through `50055`. The
development database credentials in `.env.example` must match the initial
users created in `docker/init/00-users-and-dbs.sql`.

Stop the containers while preserving database data:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml down
```

To reset the databases and run a clean initialization, add `-v` to `down`.
This deletes the Compose MySQL data volume.

>>>>>>> 6405387b811562415ad5d1707c27bab071354d52
