exports.requireRole = (role) => (req, res, next) =>
  req.user && req.user.role === role
    ? next()
    : res.status(403).render('error', { status: 403, message: 'You do not have permission to view this page.' });