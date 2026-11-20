-- Table-level grants follow table creation; account/database creation is in 00-users-and-dbs.sql.
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

GRANT SELECT ON grades_db.grades TO 'grades_view_user'@'%';
GRANT SELECT, INSERT, UPDATE ON grades_db.grades TO 'grades_upload_user'@'%';

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
