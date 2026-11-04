const grpc = require('../grpc');
const handleError = require('../middleware/error');

const GRADE_OPTIONS = ['4.0', '3.5', '3.0', '2.5', '2.0', '1.5', '1.0', '0.0']; // confirm the scale with your team

exports.myGrades = async (req, res) => {
  try {
    const data = await grpc.gradesView.getMyGrades(req.token, {});
    res.render('my-grades', { grades: data.grades });
  } catch (err) {
    handleError(err, req, res, 'Grades');
  }
};

exports.showUpload = (req, res) => res.render('upload-grades', { gradeOptions: GRADE_OPTIONS });

exports.upload = async (req, res) => {
  const values = {
    student_username: (req.body.student_username || '').trim(),
    course_code: (req.body.course_code || '').trim(),
    term: (req.body.term || '').trim(),
    grade: (req.body.grade || '').trim(),
  };
  const rerender = (msg) => res.status(400).render('upload-grades', { values, gradeOptions: GRADE_OPTIONS, error: msg });

  if (Object.values(values).some(v => !v)) return rerender('All fields are required.');
  if (values.student_username.length > 32 || values.course_code.length > 16) return rerender('Student ID or course code is too long.');
  if (!/^\d{4}-T\d$/.test(values.term)) return rerender('Term must look like 2025-T1.');
  if (!GRADE_OPTIONS.includes(values.grade)) return rerender('Invalid grade.');

  try {
    await grpc.gradesUpload.uploadGrade(req.token, values);
    res.render('upload-grades', { gradeOptions: GRADE_OPTIONS, success: `Grade saved for ${values.student_username} in ${values.course_code}.` });
  } catch (err) {
    handleError(err, req, res, 'Grade upload', { view: 'upload-grades', data: { values, gradeOptions: GRADE_OPTIONS } });
  }
};