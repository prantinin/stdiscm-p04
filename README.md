# STDISCM Distributed Enrollment System

A distributed enrollment system consisting of a web-based frontend, five backend services, and a MySQL database. The system demonstrates distributed service communication, fault isolation, and fault recovery.

## System Architecture

| Component | Description |
|---|---|
| View | Web GUI and frontend |
| Auth Service | Handles user authentication and login |
| Catalog Service | Displays available courses |
| Enrollment Service | Handles student course enrollment |
| Grades View Service | Retrieves student grades |
| Grades Upload Service | Handles faculty grade uploads |
| MySQL | Stores application data |

---

# Requirements

Before running the application, make sure you have installed:

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- [Git for Windows](https://git-scm.com/)

You will also need the project repository and its configuration files.

---

# Running the Application

## Step 1: Start Docker Desktop

1. Open Docker Desktop.
2. Wait until Docker has finished starting.
3. Open a new PowerShell terminal.
4. Verify that Docker is installed:

```powershell
docker --version
docker compose version
```

Both commands should return version information.

## Step 2: Generate JWT Keys

From the project root, open Git Bash and run:

```bash
bash docker/gen-keys.sh
```

This generates the following files:

- `keys/private.pem`
- `keys/public.pem`

These keys are required for authentication and JWT verification.

**Note:** Only generate the keys if they are missing. Running the script overwrites the existing key pair.

## Step 3: Create the Environment File

From the project root, open PowerShell and run:

```powershell
Copy-Item .env.example .env
```

This creates the root `.env` file used by Docker Compose.

Make sure the database credentials in `.env` match the initial database users configured in `docker/init/00-users-and-dbs.sql`.

## Step 4: Build and Start the System

Run the following command from the project root:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up --build -d
```

This builds and starts the complete application in the background.

The following components will be started:

- MySQL Database
- Auth Service
- Catalog Service
- Enrollment Service
- Grades View Service
- Grades Upload Service
- Web GUI

The first build may take several minutes because Docker needs to download dependencies and build the Java services.

## Step 5: Check Container Status

To verify that all containers have started successfully, run:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml ps
```

To display the service names defined in Docker Compose:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml config --services
```

Make sure the required services are running before proceeding.

## Step 6: Open the Application

Open your browser and navigate to:

**http://localhost:8080**

The frontend should now communicate with the actual backend services and MySQL database.

Log in using the demo accounts provided below.

---

# Refreshing the Application

If you make changes to the application or encounter outdated content, you can refresh or restart it using the following methods.

### 1. Refresh the Browser

If the application is already running, simply refresh the browser:

- Windows: `Ctrl + R`
- Hard refresh: `Ctrl + Shift + R`

A hard refresh forces the browser to reload the page and its resources.

### 2. Restart the Application

If the application is not responding or you need to restart the containers, run:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml restart
```

This restarts the running containers without rebuilding the application.

### 3. Rebuild After Code Changes

If you have pulled new code or made changes that need to be included in the Docker images, run:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up --build -d
```

This rebuilds the images when necessary and starts the updated containers.

**Note:** A normal browser refresh does not rebuild the backend services. Use the rebuild command when you need Docker to incorporate code changes into the images.

---

# Demo Accounts

The application provides demo accounts for students and faculty members.

All demo accounts use the same password:

**Password:** `password123`

| Username | Role | Password |
|---|---|---|
| `12012345` | Student | `password123` |
| `12012346` | Student | `password123` |
| `12012347` | Student | `password123` |
| `12012348` | Student | `password123` |
| `12012349` | Student | `password123` |
| `fac001` | Faculty | `password123` |
| `fac002` | Faculty | `password123` |
| `fac003` | Faculty | `password123` |

The database stores per-account BCrypt password hashes rather than plaintext passwords.

---

# Database Information

The system uses MySQL to store course, enrollment, and grades information.

### Database Names

| Database | Purpose |
|---|---|
| `course_db` | Stores course and enrollment information |
| `grades_db` | Stores student grades |

### Database Accounts

The following service accounts are created by the database initialization scripts:

- `catalog_user`
- `enrollment_user`
- `grades_view_user`
- `grades_upload_user`

The backend services use their respective database accounts to access the database.

The development database credentials are configured in the root `.env` file and must match the initial users created in `docker/init/00-users-and-dbs.sql`.


# Fault Tolerance Testing

One of the main objectives of this project is to demonstrate that the failure of one backend node does not cause the entire distributed system to stop working.

The following procedures can be used to test node failure, fault isolation, and recovery.

## 1. Identify the Service Names

Run:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml config --services
```

This displays the actual service names used by Docker Compose.

Use the returned names in the following commands. The examples below assume the service names are:

- `auth`
- `catalog`
- `enrollment`
- `grades-view`
- `grades-upload`

## 2. Stop a Node

For example, to stop the Catalog Service:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml stop catalog
```

This stops the Catalog container without stopping the other services.

Check the container statuses:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml ps
```

## 3. Test the Application During the Failure

Open the application:

http://localhost:8080

Try performing the following actions:

1. Log in using a student account.
2. Open the course catalog.
3. Try enrolling in a course.
4. Open the grades page.
5. Log in using a faculty account.
6. Try accessing the grade upload page.

Observe which features remain operational and which become unavailable.

**Expected behavior:** Only the functionality dependent on the failed node should become unavailable. Other independent services should continue working.

A failed dependency may also affect related operations.

## 4. Repeat the Test for Other Nodes

After testing a node, restore it before testing another one.

To restore the Catalog Service:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml start catalog
```

Repeat the procedure for the other services.

| Node Stopped | Features to Test |
|---|---|
| Auth | Existing sessions and new logins |
| Catalog | Course listing and enrollment |
| Enrollment | Course enrollment and enrollment records |
| Grades View | Viewing student grades |
| Grades Upload | Faculty grade uploads |

**Note:** Do not stop MySQL when testing individual application nodes. This ensures that each test isolates the failure of one backend service.

## 5. Test an Unexpected Node Failure

You can also simulate an abrupt node failure instead of gracefully stopping a container.

For example, to kill the Catalog Service:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml kill catalog
```

Check the container status:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml ps
```

Open the application and repeat the functionality tests.

## 6. Test Node Recovery

After stopping or killing a node, restart it using:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up -d catalog
```

Check whether the container has returned to a running state:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml ps
```

Refresh the application and test the previously unavailable functionality.

**Expected behavior:** The recovered service should resume handling requests without requiring the entire system to restart or users to log in again.

---

# Viewing Service Logs

If a service encounters an error or becomes unavailable, you can inspect its logs.

For example, to view the Catalog Service logs:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml logs -f catalog
```

Replace `catalog` with the actual service name you want to inspect.

To view logs from all services:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml logs -f
```

Press `Ctrl + C` to stop viewing the logs.

---

# Stopping the Application

To stop all containers while preserving database data:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml down
```

To completely reset the database and remove its stored volume:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml down -v
```

**Warning:** The `-v` option deletes the database volume and all stored data. Use it only when you want a clean database initialization.

---

# Viewing DB contents

## 1. Enter the container  
```
dc exec mysql bash
```

## 2. Find your database name  
replace `yourdb` with the name of the database you want to access

```
DB=yourdb
for t in $(mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SHOW TABLES FROM $DB" 2>/dev/null); do
  echo ""; echo "=== $t ==="
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -t -e "SELECT * FROM $DB.$t" 2>/dev/null
done
```

## 3. Leave the container

```
exit
```

---


# Troubleshooting

| Problem | Possible Solution |
|---|---|
| `docker is not recognized` | Install Docker Desktop and make sure it is running. Open a new terminal afterward. |
| Docker daemon connection error | Start Docker Desktop and wait until it is ready. |
| Port `8080` is already in use | Check whether another application is using port `8080`. |
| Port `3307` is already in use | Check whether another MySQL instance is using the port. |
| JWT key error | Check whether `keys/private.pem` and `keys/public.pem` exist. |
| Login does not work | Check the Auth Service logs and verify the database configuration. |
| Frontend cannot reach backend services | Check the Docker container statuses and service logs. |
| Database connection error | Verify the database credentials and initialization scripts. |
| A feature displays unavailable | Check whether its corresponding backend service is running. |
| Application does not reflect recent changes | Hard-refresh the browser or rebuild the Docker images. |

## Important Notes

- Always run Docker Compose commands from the repository root.
- The frontend is accessible at `http://localhost:8080`.
- Make sure Docker Desktop is running before starting the application.
- Do not regenerate JWT keys unnecessarily because doing so overwrites the existing key pair.
- Do not use `docker compose down -v` unless you intend to reset the database.
- Make sure the environment variables and database credentials are configured correctly before starting the system.
- Use `docker compose up --build -d` when rebuilding the application after code changes.

---