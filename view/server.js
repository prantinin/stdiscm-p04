const path = require('path');
const express = require('express');
const cookieParser = require('cookie-parser');
const config = require('./config');
const clients = require('./grpc/clients');
const { attachUser } = require('./middleware/auth');

const app = express();
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

app.use(express.urlencoded({ extended: false })); // reads form posts
app.use(cookieParser());
app.use(express.static(path.join(__dirname, 'public')));

app.use((req, res, next) => {        // defaults so EJS never hits "undefined"
  res.locals.user = null;
  res.locals.error = null;
  res.locals.success = null;
  res.locals.values = {};
  next();
});
app.use(attachUser);
app.use('/', require('./routes'));
app.use((req, res) => res.status(404).render('error', { status: 404, message: 'Page not found.' }));

app.listen(config.port, () => console.log(`View running on http://localhost:${config.port}`));