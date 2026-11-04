const grpc = require('../grpc');
const handleError = require('../middleware/error');

exports.list = async (req, res) => {
  try {
    const data = await grpc.catalog.listCourses(req.token, { open_only: false });
    res.render('courses', { courses: data.courses });
  } catch (err) {
    handleError(err, req, res, 'Course list');
  }
};