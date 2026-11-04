const router = require('express').Router();
const { requireLogin } = require('../middleware/auth');
const { requireRole } = require('../middleware/role');
const authC = require('../controllers/authController');
const catalogC = require('../controllers/catalogController');
const enrollC = require('../controllers/enrollmentController');
const gradesC = require('../controllers/gradesController');

router.get('/', (req, res) => res.redirect(req.user ? '/courses' : '/login'));

router.get('/login', authC.showLogin);
router.post('/login', authC.login);
router.post('/logout', authC.logout);

router.get('/courses', requireLogin, catalogC.list);

router.get('/enroll', requireLogin, requireRole('STUDENT'), enrollC.showForm);
router.post('/enroll', requireLogin, requireRole('STUDENT'), enrollC.enroll);
router.get('/enrollments', requireLogin, requireRole('STUDENT'), enrollC.list);

router.get('/grades', requireLogin, requireRole('STUDENT'), gradesC.myGrades);
router.get('/grades/upload', requireLogin, requireRole('FACULTY'), gradesC.showUpload);
router.post('/grades/upload', requireLogin, requireRole('FACULTY'), gradesC.upload);

module.exports = router;