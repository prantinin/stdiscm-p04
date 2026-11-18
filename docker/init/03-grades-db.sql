-- Owned by Member 4. Mount after the auth and course database initialization scripts.
CREATE DATABASE IF NOT EXISTS grades_db;
USE grades_db;
CREATE TABLE IF NOT EXISTS grades (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_username VARCHAR(32) NOT NULL,
  course_code VARCHAR(16) NOT NULL,
  term VARCHAR(16) NOT NULL,
  grade VARCHAR(4) NOT NULL,
  uploaded_by VARCHAR(32) NOT NULL,
  uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_student_course_term (student_username, course_code, term)
);

CREATE USER IF NOT EXISTS 'grades_view_user'@'%' IDENTIFIED BY 'grades_view_password';
CREATE USER IF NOT EXISTS 'grades_upload_user'@'%' IDENTIFIED BY 'grades_upload_password';
GRANT SELECT ON grades_db.grades TO 'grades_view_user'@'%';
GRANT SELECT, INSERT, UPDATE ON grades_db.grades TO 'grades_upload_user'@'%';
FLUSH PRIVILEGES;

INSERT INTO grades (student_username, course_code, term, grade, uploaded_by) VALUES
('12012345','CCPROG3','2025-T1','3.5','fac001'),
('12012345','STADVDB','2025-T2','3.0','fac002'),
('12012346','CCPROG3','2025-T1','4.0','fac001'),
('12012346','CSARCH2','2025-T2','2.5','fac003'),
('12012347','STDISCM','2025-T1','3.0','fac002'),
('12012347','STADVDB','2025-T2','3.5','fac002'),
('12012348','CCINOV8','2025-T1','2.0','fac003'),
('12012349','CSNETWK','2025-T2','3.0','fac001')
ON DUPLICATE KEY UPDATE grade = VALUES(grade), uploaded_by = VALUES(uploaded_by);
