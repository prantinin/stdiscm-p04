CREATE DATABASE IF NOT EXISTS auth_db;
USE auth_db;

CREATE TABLE IF NOT EXISTS users (
  username      VARCHAR(32) PRIMARY KEY,
  password_hash CHAR(60) NOT NULL,
  full_name     VARCHAR(100) NOT NULL,
  role          ENUM('STUDENT', 'FACULTY') NOT NULL
);

CREATE TABLE IF NOT EXISTS revoked_tokens (
  jti        VARCHAR(64) PRIMARY KEY,
  expires_at DATETIME NOT NULL
);

CREATE USER IF NOT EXISTS 'auth_user'@'%' IDENTIFIED BY 'auth_password';
GRANT SELECT ON auth_db.users TO 'auth_user'@'%';
GRANT SELECT, INSERT, UPDATE ON auth_db.revoked_tokens TO 'auth_user'@'%';
FLUSH PRIVILEGES;

-- Demo password for every account: password123. Each stored value is a distinct BCrypt hash.
-- Student IDs align with the course/grades data; faculty IDs align with the demo frontend.
INSERT INTO users (username, password_hash, full_name, role) VALUES
  ('12012345', '$2a$10$y6uXssrAAwZD63rPiYNtV.otGwAq4pM9cPwmBOvCHa1NCPkB4/jiS', 'Juan Dela Cruz',  'STUDENT'),
  ('12012346', '$2a$10$5K8uBESNxwjrwORu.o5K2eyT1h6An7H4DjfPIEsMvuZWvyB08BZKi', 'Maria Santos',    'STUDENT'),
  ('12012347', '$2a$10$k0CqP/zASO3H0GeQLej4f.3B0AeeL1NCZOibtaeaiQeWfbEJ9fHPO', 'Pedro Reyes',     'STUDENT'),
  ('12012348', '$2a$10$DetF8jwFpM6Xa1B9gNfODuWLxN2foL1QqOIRXlSALuPrXyq9SwTK6', 'Ana Garcia',      'STUDENT'),
  ('12012349', '$2a$10$tMHtcKrtiEdFAffmVMb6Y.MHVibdRt5.ZU5czFt9MoC7HyegbYR3a', 'Luis Mendoza',    'STUDENT'),
  ('fac001',   '$2a$10$QixuOEsLkWt4UaaI0OqowucwYVHjYtV.lVe95BTlUXtiEDZJ6GuAi', 'Dr. Elena Cruz',  'FACULTY'),
  ('fac002',   '$2a$10$ww.CYmkMZqvF0FxDJqIqLOlIdK4pKfk/Z8Ty8pL31x6wUkxVS6I1m', 'Dr. Miguel Reyes', 'FACULTY'),
  ('fac003',   '$2a$10$DFGLStlZw3XMRb4oIvh.VuWDtXgJwb49yiBP/VNepsQX7NT991YJi', 'Dr. Sofia Lim',   'FACULTY')
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  full_name = VALUES(full_name),
  role = VALUES(role);
