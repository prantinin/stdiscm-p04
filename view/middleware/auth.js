const fs = require('fs');
const jwt = require('jsonwebtoken');
const config = require('../config');

let publicKey = null;
try { publicKey = fs.readFileSync(config.publicKeyPath); }
catch (e) { console.error('Cannot read public key:', config.publicKeyPath); } // don't crash

exports.attachUser = (req, res, next) => {
  req.user = null;
  req.token = null;
  const token = req.cookies[config.cookieName];
  if (token && publicKey) {
    try {
      const p = jwt.verify(token, publicKey, { algorithms: ['RS256'], issuer: 'enrollment-auth' });
      req.user = { username: p.sub, role: p.role, name: p.name };
      req.token = token;
    } catch (e) {
      res.clearCookie(config.cookieName); // expired or tampered
    }
  }
  res.locals.user = req.user; // navbar reads this, no service call
  next();
};

exports.requireLogin = (req, res, next) => (req.user ? next() : res.redirect('/login'));