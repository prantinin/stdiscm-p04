-- Owned by Member 3. Member 2 should mount this in MySQL's /docker-entrypoint-initdb.d.
CREATE DATABASE IF NOT EXISTS course_db;
USE course_db;
CREATE TABLE courses (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(16) NOT NULL, name VARCHAR(100) NOT NULL,
  section VARCHAR(8) NOT NULL, schedule VARCHAR(50) NOT NULL, capacity INT NOT NULL,
  status ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN', faculty_name VARCHAR(100) NOT NULL,
  UNIQUE (code, section), CONSTRAINT capacity_positive CHECK (capacity > 0)
);
CREATE TABLE enrollments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, student_username VARCHAR(32) NOT NULL, course_id BIGINT NOT NULL,
  enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (course_id) REFERENCES courses(id), UNIQUE (student_username, course_id)
);
CREATE USER IF NOT EXISTS 'catalog_user'@'%' IDENTIFIED BY 'catalog_password';
CREATE USER IF NOT EXISTS 'enrollment_user'@'%' IDENTIFIED BY 'enrollment_password';
GRANT SELECT ON course_db.courses TO 'catalog_user'@'%';
GRANT SELECT ON course_db.enrollments TO 'catalog_user'@'%';
GRANT SELECT ON course_db.courses TO 'enrollment_user'@'%';
GRANT SELECT, INSERT ON course_db.enrollments TO 'enrollment_user'@'%';
FLUSH PRIVILEGES;
INSERT INTO courses (code,name,section,schedule,capacity,status,faculty_name) VALUES
('CSADPRG','Advanced Programming','S11','MW 09:15-10:45',40,'OPEN','Dr. Elena Cruz'),
('STDISCM','Distributed Computing','S12','TH 13:00-16:00',1,'OPEN','Dr. Miguel Reyes'),
('STADVDB','Advanced Databases','S13','F 09:15-12:15',30,'CLOSED','Dr. Sofia Lim'),
('CCPROG3','Object-Oriented Programming','S14','MW 11:00-12:30',35,'OPEN','Prof. Daniel Tan'),
('CCINOV8','Web Application Development','S15','TTH 09:15-10:45',35,'OPEN','Prof. Anna Santos'),
('CSARCH2','Computer Architecture','S16','TTH 11:00-12:30',32,'OPEN','Prof. Ramon Garcia'),
('STALGCM','Algorithms and Complexity','S17','MW 13:00-14:30',30,'OPEN','Dr. Bea Lim'),
('CSNETWK','Computer Networks','S18','F 13:00-16:00',28,'CLOSED','Prof. Leo Reyes');
