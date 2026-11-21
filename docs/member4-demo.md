# Faculty Grade Management walkthrough

The Grades Upload gRPC service now also lists, edits, and deletes grade records uploaded by the authenticated faculty member. It uses the verified JWT subject as the owner; no faculty username is accepted from the browser. Grades View continues to return only the authenticated student's grades.

## Existing database volumes

The initialization grant in `docker/init/03-grades-db.sql` now includes `DELETE`. MySQL does not rerun initialization scripts for an existing data volume. To add the permission without deleting or resetting data, run this from the repository root:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml exec mysql mysql -uroot -p
```

Enter the `MYSQL_ROOT_PASSWORD` value from `.env`. At the MySQL prompt, run:

```sql
GRANT DELETE ON grades_db.grades TO 'grades_upload_user'@'%';
FLUSH PRIVILEGES;
```

Then exit the MySQL prompt. This changes a table privilege only and preserves the database volume.

## Start and open the application

Ensure Docker Desktop is running and the JWT key pair exists. From the repository root, rebuild and start the stack without removing its volume:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up --build -d
```

Check the services with `docker compose --env-file .env -f docker/docker-compose.yml ps`, then open <http://localhost:8080>. Seeded faculty accounts are `fac001`, `fac002`, and `fac003`; the demo password is `password123`.

## Manual acceptance walkthrough

1. Sign in as `fac001` and upload a grade for student `12012345` using any course from the course selector, year `2026`, Term 1, and grade `3.5`.
2. Select **Manage Grades**. The table should show the record you uploaded, including student ID, course code, year, term, grade, uploader, and timestamp.
3. Sign in as `fac002`. Its Manage Grades page should not show the `fac001` record.
4. As `fac001`, edit the row and change only the grade to `4.0`. Confirm the student/course/term and uploader did not change.
5. Try an invalid grade by submitting a modified request (for example, `1.7`). The server should return `INVALID_ARGUMENT` and preserve the stored grade.
6. As a student, try opening `/grades/manage` or posting to an edit/delete URL. The View Node should deny access. The gRPC service also enforces faculty-only access.
7. As `fac001`, choose **Delete** and accept the browser confirmation. The row should disappear from Manage Grades. Sign in as `12012345`; the deleted row should no longer appear in My Grades.
8. Try changing the URL's grade ID to another faculty member's record. The service should return `NOT_FOUND` and leave that record unchanged.

The grade list is sorted by term, course code, student ID, then record ID. The grades table stores course codes but no course names, so Manage Grades displays the code. The list includes records with T1–T4 terms already in the database; the existing upload form accepts T1–T3.

## Automated tests

From the repository root, with JDK 17 and Maven installed:

```powershell
mvn -f java/pom.xml -pl grades-view-service,grades-upload-service -am test
```

The tests cover faculty-only listing and ownership, editing a grade without creating a duplicate, invalid values, faculty-only edit/delete, deletion by record ID without affecting other rows, authentication rejection, and student grade visibility after a database deletion. The test command must actually be run before recording these checks as passed.
