# Member 4 demo walkthrough and test record

Run from a clean Compose stack after Member 2 supplies `docker/docker-compose.yml`, Auth, and `security-common`. Start with `docker compose -f docker/docker-compose.yml up --build -d`, then use the seeded demo users and their passwords from the Auth seed.

## Normal flow

1. Log in as faculty and upload a grade for `12012345`, course `CCPROG3`, term `2026-T1`, grade `3.5`.
2. Log in as student `12012345`; open My Grades and confirm that row appears in term/course order.
3. Confirm student `12023456` cannot see the first student's row.
4. Confirm a student upload request returns `PERMISSION_DENIED`; invalid grade or term returns `INVALID_ARGUMENT`.
5. Upload the same student/course/term again with a different valid grade and confirm the row is updated rather than duplicated.

## Node independence

Keep a student and faculty session logged in, then check each row. Restart the stopped node before moving on.

| Action | Expected result | Pass/fail |
| --- | --- | --- |
| `docker stop grades-view-node` | Student grades page reports unavailable; faculty upload still works | Pending |
| `docker start grades-view-node` | Student grades page recovers without restarting View or re-login | Pending |
| `docker stop grades-upload-node` | Faculty upload reports unavailable; student grades still work | Pending |
| `docker start grades-upload-node` | Faculty upload recovers without restarting View or re-login | Pending |
| Stop both grade nodes, then start them in either order | Each feature recovers independently; no re-login while token is valid | Pending |

## Final system acceptance record

Complete the project-wide checklist in §10 after Compose and all five backend nodes are present. Current checkout cannot record a valid full-stack pass: the Compose file and real Auth/security modules are pending. Run the rows below in that integrated checkout and replace Pending with Pass/Fail plus a bug reference.

| §10 scenario | Result | Bug / notes |
| --- | --- | --- |
| Student login, catalog, enroll, enrollments, logout | Pending | |
| Duplicate, closed, full, and concurrent final-seat enrollments | Pending | |
| Faculty uploads a grade and student sees it | Pending | |
| Role restrictions, user isolation, invalid credentials/cookies | Pending | |
| Each backend node stopped independently | Pending | |
| All but one backend stopped; each remaining feature works | Pending | |
| All backends stopped and restarted in arbitrary order | Pending | |
| Start with one backend absent; remaining nodes boot and work | Pending | |
