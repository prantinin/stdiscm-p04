const path = require('path');
const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const config = require('../config');

// keepCase keeps snake_case names like open_only; longs: Number turns int64 into JS numbers
const loaderOpts = { keepCase: true, longs: Number, defaults: true };
const load = (file) =>
  grpc.loadPackageDefinition(protoLoader.loadSync(path.join(config.protoDir, file), loaderOpts));

const authPkg = load('auth.proto').enrollment.auth;
const catalogPkg = load('catalog.proto').enrollment.catalog;
const enrollPkg = load('enrollment.proto').enrollment.enroll;
const gradesViewPkg = load('grades_view.proto').enrollment.gradesview;
const gradesUploadPkg = load('grades_upload.proto').enrollment.gradesupload;

// Created ONCE at startup. This does not fail if a service is down, and it reconnects by itself later.
const creds = grpc.credentials.createInsecure();
const authClient = new authPkg.AuthService(config.addr.auth, creds);
const catalogClient = new catalogPkg.CatalogService(config.addr.catalog, creds);
const enrollClient = new enrollPkg.EnrollmentService(config.addr.enrollment, creds);
const gradesViewClient = new gradesViewPkg.GradesViewService(config.addr.gradesView, creds);
const gradesUploadClient = new gradesUploadPkg.GradeUploadService(config.addr.gradesUpload, creds);

function call(client, method, request, token) {
  return new Promise((resolve, reject) => {
    const md = new grpc.Metadata();
    if (token) md.set('authorization', 'Bearer ' + token);              // behavior 3
    const options = { deadline: Date.now() + config.timeoutMs };        // behavior 4
    client[method](request, md, options, (err, res) => (err ? reject(err) : resolve(res)));
  });
}

exports.auth = {
  login: (_t, req) => call(authClient, 'Login', req, null),      // no token on login
  logout: (_t, req) => call(authClient, 'Logout', req, null),    // the token is in the request body
};
exports.catalog = { listCourses: (t, req) => call(catalogClient, 'ListCourses', req, t) };
exports.enrollment = {
  enrollStudent: (t, req) => call(enrollClient, 'EnrollStudent', req, t),
  listMyEnrollments: (t, req) => call(enrollClient, 'ListMyEnrollments', req, t),
};
exports.gradesView = { getMyGrades: (t, req) => call(gradesViewClient, 'GetMyGrades', req, t) };
exports.gradesUpload = { uploadGrade: (t, req) => call(gradesUploadClient, 'UploadGrade', req, t) };