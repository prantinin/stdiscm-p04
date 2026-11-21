# Member 4: Grades demo and acceptance checklist

This walkthrough covers the Grades View and Grades Upload nodes integrated with View, Auth, Catalog, `security-common`, and `grades_db` in the current Compose stack.

## Start the stack

From the repository root, create `.env` from `.env.example` and ensure `keys/private.pem` and `keys/public.pem` exist. If you need to generate keys, run `bash docker/gen-keys.sh`; it overwrites any existing pair. Start the system with:

```sh
docker compose --env-file .env -f docker/docker-compose.yml up --build -d
```

All seeded accounts use password `password123`. Faculty usernames are `fac001`, `fac002`, and `fac003`. Student usernames include `12012345` and `12012346`.

## Normal workflow

1. Log in as `fac001` and upload a grade for student `12012345`, course `CCPROG3`, year `2026`, Term 1, grade `3.5`.
2. Log in as student `12012345`, open **My Grades**, and confirm the row appears in term/course order.
3. Log in as student `12012346` and confirm the first student's row is not shown.
4. As a student, attempt to open **Upload Grades** and submit a grade. Both the View routes and gRPC service must deny access.
5. As faculty, try an invalid term (`2026-T4`), an invalid grade (`1.7`), missing required fields, and values beyond the username/course-code length limits. Each must be rejected.
6. Upload the same student/course/term again with grade `4.0`; confirm the existing row is updated instead of duplicated.

The grade service authenticates signed RS256 bearer tokens using `security-common`. Grades View derives the student identity from the verified JWT subject; the request contains no student username. Grades Upload derives `uploaded_by` from the verified faculty subject. The accepted terms are `YYYY-T1`, `YYYY-T2`, and `YYYY-T3`; grades are `4.0`, `3.5`, `3.0`, `2.5`, `2.0`, `1.5`, `1.0`, or `0.0`.

## Automated grades-service tests

From the repository root, run:

```sh
mvn -f java/pom.xml -pl grades-view-service,grades-upload-service -am test
```

`GradesViewServiceTest` covers verified student filtering, result ordering, role denial, and rejection of unsigned tokens. `GradesUploadServiceTest` covers faculty uploads, role denial, required fields, lengths, allowed grades, rejection of Term 4, and duplicate-key updates.

## Service independence and recovery

Keep a student and faculty session available. Stop each grade node, verify only its feature becomes unavailable, restart it, then retry without restarting View or logging in again.

| Action | Expected result | Result / notes |
| --- | --- | --- |
| `docker stop grades-view-node` | My Grades reports unavailable; faculty upload still works | Not yet recorded |
| `docker start grades-view-node` | My Grades recovers | Not yet recorded |
| `docker stop grades-upload-node` | Upload reports unavailable; My Grades still works | Not yet recorded |
| `docker start grades-upload-node` | Upload recovers | Not yet recorded |
| Stop both grade nodes; restart in either order | Each feature recovers independently | Not yet recorded |

## Architecture limitation

Grades Upload validates the student identifier, course code, term, and grade, and records the faculty username from the verified JWT subject. It does not call Catalog or Enrollment to verify faculty-course assignments or student enrollment, preserving backend-node independence. Confirm this limitation against the official assignment rubric if available.

## Acceptance record

Replace each result with Pass/Fail and a short note after running the scenario. A pending entry means the behavior has not been demonstrated.

| Scenario | Result / notes |
| --- | --- |
| Faculty upload is visible to the intended student | Not yet recorded |
| A student cannot view another student's grades | Not yet recorded |
| Students cannot upload; faculty can upload | Not yet recorded |
| Invalid term, grade, required field, and lengths are rejected | Not yet recorded |
| Re-upload updates the existing row | Not yet recorded |
| Grades View and Grades Upload fail and recover independently | Not yet recorded |