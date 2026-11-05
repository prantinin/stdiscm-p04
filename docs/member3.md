# Member 3: Catalog and Enrollment services

## What is implemented

- `catalog-service` is a read-only gRPC node on port 50052. It lists all or only open courses, including a database-derived enrollment count.
- `enrollment-service` is a gRPC node on port 50053. A student can enroll by course code and section, or list only their own enrollments.
- The enrollment flow uses a single database transaction and locks the course row before checking capacity. The database also prevents duplicate enrollment with a unique key.
- `docker/init/02-course-db.sql` creates and seeds `course_db` and the two least-privilege accounts. It is intended for Member 2 to mount into MySQL initialization.

Neither service calls the other service. Both read the same MySQL database directly, as required for node independence.

## Development-only security stub - required follow-up

`java/dev-security-stub` is deliberately temporary. It reads the claims from a JWT payload but **does not verify its signature**. It exists only so these two nodes can integrate with the View while Member 2's `security-common` module is unavailable. Do not treat this as secure or deploy it.

When Member 2 finishes, replace `dev-security-stub` in both service POMs with Member 2's `security-common` artifact, remove `java/dev-security-stub`, and change these references:

1. Replace `DevAuthInterceptor` on each server with Member 2's `AuthInterceptor`.
2. Replace `DevAuthInterceptor.USER.get()` with `AuthContext.current()`.
3. Preserve the current role rules: Catalog accepts either authenticated role; both Enrollment RPCs require `STUDENT`.
4. Run the full Maven test and Docker fault-tolerance checklist again. A missing/invalid/tampered token must then return `UNAUTHENTICATED` based on real RS256 verification.

## Configuration

| Variable | Catalog default | Enrollment default |
| --- | --- | --- |
| `GRPC_PORT` | `50052` | `50053` |
| `DB_URL` | `jdbc:mysql://localhost:3306/course_db` | same |
| `DB_USER` | `catalog_user` | `enrollment_user` |
| `DB_PASSWORD` | `catalog_password` | `enrollment_password` |
| `DB_POOL_SIZE` | `5` | `5` |

The production Compose configuration should use `mysql:3306` in `DB_URL`, not `localhost`, and should mount `docker/init/02-course-db.sql` into `/docker-entrypoint-initdb.d/02-course-db.sql`.

## Build and run

From the repository root:

```sh
mvn -f java/pom.xml package
DB_URL=jdbc:mysql://localhost:3306/course_db DB_USER=catalog_user DB_PASSWORD=catalog_password \
  java -jar java/catalog-service/target/catalog-service-1.0.0-SNAPSHOT.jar
DB_URL=jdbc:mysql://localhost:3306/course_db DB_USER=enrollment_user DB_PASSWORD=enrollment_password \
  java -jar java/enrollment-service/target/enrollment-service-1.0.0-SNAPSHOT.jar
```

For Docker builds, use the repository root as the build context:

```sh
docker build -f java/catalog-service/Dockerfile -t enrollment-catalog .
docker build -f java/enrollment-service/Dockerfile -t enrollment-enrollment .
```

## Checks to run after integration

1. Log in as a student and verify the Courses page and My Enrollments page work.
2. Enroll in an open course, then retry: the second request must return `ALREADY_EXISTS`.
3. Try a closed course and the one-seat `STDISCM/S12` course after it is full: both must return `FAILED_PRECONDITION`.
4. Start two student requests for the final `STDISCM/S12` seat simultaneously: exactly one succeeds.
5. Stop `catalog-node`: only course listing should fail; enrollment by course code/section and My Enrollments should still work.
6. Stop `enrollment-node`: only enrollment and My Enrollments should fail; Catalog should still work.
