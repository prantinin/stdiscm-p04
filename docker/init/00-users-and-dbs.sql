CREATE DATABASE IF NOT EXISTS auth_db;
CREATE DATABASE IF NOT EXISTS course_db;
CREATE DATABASE IF NOT EXISTS grades_db;

CREATE USER IF NOT EXISTS 'auth_user'@'%' IDENTIFIED BY 'auth_password';
CREATE USER IF NOT EXISTS 'catalog_user'@'%' IDENTIFIED BY 'catalog_password';
CREATE USER IF NOT EXISTS 'enroll_user'@'%' IDENTIFIED BY 'enrollment_password';
CREATE USER IF NOT EXISTS 'grades_view_user'@'%' IDENTIFIED BY 'grades_view_password';
CREATE USER IF NOT EXISTS 'grades_upload_user'@'%' IDENTIFIED BY 'grades_upload_password';
