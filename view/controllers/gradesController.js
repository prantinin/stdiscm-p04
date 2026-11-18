const grpc = require('../grpc');
const handleError = require('../middleware/error');
const { status } = require('@grpc/grpc-js');
const config = require('../config');

const GRADE_OPTIONS = ['4.0', '3.5', '3.0', '2.5', '2.0', '1.5', '1.0', '0.0']; // confirm the scale with your team

exports.myGrades = async (req, res) => {
  try {
    const data = await grpc.gradesView.getMyGrades(req.token, {});
    res.render('my-grades', { grades: data.grades });
  } catch (err) {
    handleError(err, req, res, 'Grades');
  }
};

exports.showUpload = (req, res) => res.render('upload-grades', {
  gradeOptions: GRADE_OPTIONS,
  values: {},
});

// The form loads its course list asynchronously so Catalog being unavailable
// affects this form only, not View startup or any other page.
exports.uploadCourses = async (req, res) => {
  try {
    const data = await grpc.catalog.listCourses(req.token, { open_only: false });
    res.json({ courses: data.courses || [] });
  } catch (err) {
    if (err.code === status.UNAUTHENTICATED) {
      res.clearCookie(config.cookieName);
      return res.status(401).json({ error: 'Session expired. Please log in again.' });
    }
    if (err.code === status.PERMISSION_DENIED) return res.status(403).json({ error: 'You do not have permission to view courses.' });
    if (err.code === status.UNAVAILABLE || err.code === status.DEADLINE_EXCEEDED) {
      return res.status(503).json({ error: 'Course list is currently unavailable. Please try again later.' });
    }
    console.error('[Grade upload courses]', err);
    return res.status(500).json({ error: 'Unable to load courses.' });
  }
};

exports.upload = async (req, res) => {
  const academicYearChoice = (req.body.academic_year || '').trim();
  const olderYear = (req.body.older_year || '').trim();
  const termNumber = (req.body.term_number || '').trim();
  let year = '';
  if (academicYearChoice === 'older') {
    if (/^\d{1,4}$/.test(olderYear) && Number(olderYear) >= 1 && Number(olderYear) < 2020) {
      year = String(Number(olderYear)).padStart(4, '0');
    }
  } else if (/^\d{4}$/.test(academicYearChoice)) {
    const numericYear = Number(academicYearChoice);
    if (numericYear >= 2020 && numericYear <= new Date().getFullYear()) year = academicYearChoice;
  }

  const values = {
    student_username: (req.body.student_username || '').trim(),
    course_code: (req.body.course_code || '').trim(),
    academic_year: academicYearChoice,
    older_year: olderYear,
    term_number: termNumber,
    term: year && /^[1-4]$/.test(termNumber) ? `${year}-T${termNumber}` : '',
    grade: (req.body.grade || '').trim(),
  };
  const rerender = (msg) => res.status(400).render('upload-grades', { values, gradeOptions: GRADE_OPTIONS, error: msg });

  if (!values.student_username || !values.course_code || !values.grade) return rerender('Student ID, course, and grade are required.');
  if (!year) return rerender(academicYearChoice === 'older' ? 'Choose a valid year before 2020.' : 'Choose an academic year from 2020 through the current year.');
  if (!/^[1-4]$/.test(termNumber)) return rerender('Choose a valid academic term (Term 1 through Term 4).');
  if (values.student_username.length > 32 || values.course_code.length > 16) return rerender('Student ID or course code is too long.');
  if (!/^\d{4}-T[1-4]$/.test(values.term)) return rerender('Choose a valid academic year and term.');
  if (!GRADE_OPTIONS.includes(values.grade)) return rerender('Invalid grade.');

  try {
    await grpc.gradesUpload.uploadGrade(req.token, {
      student_username: values.student_username,
      course_code: values.course_code,
      term: values.term,
      grade: values.grade,
    });
    res.render('upload-grades', { gradeOptions: GRADE_OPTIONS, success: `Grade saved for ${values.student_username} in ${values.course_code}.` });
  } catch (err) {
    handleError(err, req, res, 'Grade upload', { view: 'upload-grades', data: { values, gradeOptions: GRADE_OPTIONS } });
  }
};
