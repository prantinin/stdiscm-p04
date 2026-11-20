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

