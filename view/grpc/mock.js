const fs = require('fs');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { status } = require('@grpc/grpc-js');
const config = require('../config');

const nowSec = () => Math.floor(Date.now() / 1000);
const fail = (code, details) => { const e = new Error(details); e.code = code; e.details = details; throw e; };
const checkDead = (name) => { if (config.deadNodes.includes(name)) fail(status.UNAVAILABLE, 'down'); };
const who = (token) => jwt.decode(token).sub;

const DEMO_PASSWORD = 'password123';
const USERS = {
  '12012345': { name: 'Juan Dela Cruz', role: 'STUDENT' },
  '12012346': { name: 'Maria Santos', role: 'STUDENT' },
  '12012347': { name: 'Pedro Reyes', role: 'STUDENT' },
  '12012348': { name: 'Ana Garcia', role: 'STUDENT' },
  '12012349': { name: 'Luis Mendoza', role: 'STUDENT' },
  fac001: { name: 'Dr. Elena Cruz', role: 'FACULTY' },
  fac002: { name: 'Dr. Miguel Reyes', role: 'FACULTY' },
  fac003: { name: 'Dr. Sofia Lim', role: 'FACULTY' },
};

const COURSES = [
  { id: 1, code: 'CSADPRG', name: 'Advanced Programming', section: 'S11', schedule: 'MW 9:15-10:45', capacity: 40, enrolled: 12, status: 'OPEN', faculty_name: 'Dr. Elena Cruz' },
  { id: 2, code: 'STDISCM', name: 'Distributed Computing', section: 'S12', schedule: 'TH 13:00-16:00', capacity: 1, enrolled: 1, status: 'OPEN', faculty_name: 'Dr. Miguel Reyes' },
  { id: 3, code: 'STADVDB', name: 'Advanced Databases', section: 'S13', schedule: 'F 9:15-12:15', capacity: 30, enrolled: 30, status: 'CLOSED', faculty_name: 'Dr. Sofia Lim' },
];

const myEnrollments = [];   // in memory only; resets when the server restarts
const grades = [
  { student_username: '12012345', course_code: 'CSINTSY', term: '2025-T1', grade: '3.5', uploaded_by: 'fac001', uploaded_at: nowSec() },
  { student_username: '12012345', course_code: 'CCPROG3', term: '2025-T2', grade: '4.0', uploaded_by: 'fac002', uploaded_at: nowSec() },
];

exports.auth = {
  async login(_t, { username, password }) {
    checkDead('auth');
    const u = USERS[username];
    if (!u || password !== DEMO_PASSWORD) fail(status.UNAUTHENTICATED, 'Invalid username or password');
    const privateKey = fs.readFileSync(config.devPrivateKeyPath);
    const token = jwt.sign({ role: u.role, name: u.name }, privateKey, {
      algorithm: 'RS256', subject: username, issuer: 'enrollment-auth',
      expiresIn: '60m', jwtid: crypto.randomUUID(),
    });
    return { token, username, full_name: u.name, role: u.role, expires_at: jwt.decode(token).exp };
  },
  async logout() { checkDead('auth'); return {}; },
};

exports.catalog = {
  async listCourses(_t, { open_only }) {
    checkDead('catalog');
    return { courses: open_only ? COURSES.filter(c => c.status === 'OPEN') : COURSES };
  },
};

exports.enrollment = {
  async enrollStudent(token, { course_code, section }) {
    checkDead('enrollment');
    const c = COURSES.find(x => x.code === course_code && x.section === section);
    if (!c) fail(status.NOT_FOUND, 'Course not found');
    if (c.status !== 'OPEN' || c.enrolled >= c.capacity) fail(status.FAILED_PRECONDITION, 'Course is full or closed');
    const me = who(token);
    if (myEnrollments.some(e => e.user === me && e.course_code === c.code)) {
      fail(status.ALREADY_EXISTS, 'You are already enrolled in this course');
    }
    const record = { id: myEnrollments.length + 1, course_code: c.code, section: c.section, course_name: c.name, schedule: c.schedule, enrolled_at: nowSec() };
    myEnrollments.push({ user: me, ...record });
    return { record };
  },
  async listMyEnrollments(token) {
    checkDead('enrollment');
    return { records: myEnrollments.filter(e => e.user === who(token)) };
  },
};

exports.gradesView = {
  async getMyGrades(token) {
    checkDead('gradesView');
    return { grades: grades.filter(g => g.student_username === who(token)) };
  },
};

exports.gradesUpload = {
  async uploadGrade(token, r) {
    checkDead('gradesUpload');
    const row = { ...r, uploaded_by: who(token), uploaded_at: nowSec() };
    const i = grades.findIndex(g => g.student_username === r.student_username && g.course_code === r.course_code && g.term === r.term);
    if (i >= 0) grades[i] = row; else grades.push(row);
    return row;
  },
};