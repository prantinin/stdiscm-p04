const { status } = require('@grpc/grpc-js');
const grpc = require('../grpc');
const config = require('../config');
const { toMs } = require('../utils/utils');
const handleError = require('../middleware/error');

exports.showLogin = (req, res) => {
  if (req.user) return res.redirect('/courses');
  res.render('login', { error: req.query.expired ? 'Session expired, please log in again.' : null });
};

exports.login = async (req, res) => {
  const username = (req.body.username || '').trim();
  const password = req.body.password || '';
  if (!username || !password || username.length > 100 || password.length > 100) {
    return res.status(400).render('login', { error: 'Username and password are required.' });
  }
  try {
    const data = await grpc.auth.login(null, { username, password });
    res.cookie(config.cookieName, data.token, {
      httpOnly: true,
      sameSite: 'lax',
      maxAge: Math.max(toMs(data.expires_at) - Date.now(), 0),
    });
    res.redirect('/courses');
  } catch (err) {
    if (err.code === status.UNAUTHENTICATED) {
      return res.status(401).render('login', { error: 'Invalid username or password.' });
    }
    if (err.code === status.UNAVAILABLE || err.code === status.DEADLINE_EXCEEDED) {
      return res.status(503).render('login', { error: 'Login is unavailable, please try again later.' });
    }
    handleError(err, req, res, 'Login');
  }
};

exports.logout = async (req, res) => {
  try {
    if (req.token) await grpc.auth.logout(null, { token: req.token });
  } catch (err) {
    console.error('Logout call failed:', err.code); // ignore, still log out
  }
  res.clearCookie(config.cookieName);
  res.redirect('/login');
};