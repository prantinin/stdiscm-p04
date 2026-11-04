const grpc = require('../grpc');
const handleError = require('../middleware/error');
const { fmtDate } = require('../utils/utils');

exports.showForm = (req, res) => res.render('enroll');

exports.enroll = async (req, res) => {
  const values = {
    course_code: (req.body.course_code || '').trim(),
    section: (req.body.section || '').trim(),
  };
  if (!values.course_code || !values.section || values.course_code.length > 16 || values.section.length > 8) {
    return res.status(400).render('enroll', { values, error: 'Course code and section are required.' });
  }
  try {
    const data = await grpc.enrollment.enrollStudent(req.token, values);
    res.render('enroll', { success: `Enrolled in ${data.record.course_code} (${data.record.section}).` });
  } catch (err) {
    handleError(err, req, res, 'Enrollment', { view: 'enroll', data: { values } });
  }
};

exports.list = async (req, res) => {
  try {
    const data = await grpc.enrollment.listMyEnrollments(req.token, {});
    const records = data.records.map(r => ({ ...r, enrolled_at: fmtDate(r.enrolled_at) }));
    res.render('my-enrollments', { records });
  } catch (err) {
    handleError(err, req, res, 'My Enrollments');
  }
};