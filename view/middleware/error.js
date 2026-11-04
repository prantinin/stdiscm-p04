const { status } = require('@grpc/grpc-js');
const config = require('../config');

// feature = "Course list", "Enrollment", ...
// form = { view, data } for pages with a form, so the message shows on the same form
module.exports = function handleError(err, req, res, feature, form) {
  const code = err.code;

  if (code === status.UNAVAILABLE || code === status.DEADLINE_EXCEEDED) {
    return res.status(503).render('error', {
      status: 503,
      message: `${feature} is currently unavailable, please try again later`,
    });
  }
  if (code === status.UNAUTHENTICATED) {
    res.clearCookie(config.cookieName);
    return res.redirect('/login?expired=1');
  }
  if (code === status.PERMISSION_DENIED) {
    return res.status(403).render('error', { status: 403, message: 'You do not have permission to do that.' });
  }
  const formErrors = [status.INVALID_ARGUMENT, status.NOT_FOUND, status.ALREADY_EXISTS, status.FAILED_PRECONDITION];
  if (formErrors.includes(code) && form) {
    return res.status(400).render(form.view, { ...form.data, error: err.details || 'Request failed.' });
  }
  console.error(`[${feature}]`, err);
  res.status(500).render('error', { status: 500, message: 'Something went wrong.' });
};