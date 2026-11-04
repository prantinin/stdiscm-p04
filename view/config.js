const path = require('path');
const list = (k) => (process.env[k] || '').split(',').map(s => s.trim()).filter(Boolean);

module.exports = {
  port: Number(process.env.PORT || 8080),
  cookieName: process.env.SESSION_COOKIE_NAME || 'enroll_token',
  timeoutMs: Number(process.env.GRPC_TIMEOUT_MS || 2000),
  publicKeyPath: process.env.JWT_PUBLIC_KEY_PATH || path.resolve(__dirname, '../keys/public.pem'),
  devPrivateKeyPath: process.env.DEV_PRIVATE_KEY_PATH || path.resolve(__dirname, '../keys/private.pem'), // mocks only
  protoDir: process.env.PROTO_DIR || path.resolve(__dirname, '../proto'),
  addr: {
    auth: process.env.AUTH_ADDR || 'localhost:50051',
    catalog: process.env.CATALOG_ADDR || 'localhost:50052',
    enrollment: process.env.ENROLLMENT_ADDR || 'localhost:50053',
    gradesView: process.env.GRADES_VIEW_ADDR || 'localhost:50054',
    gradesUpload: process.env.GRADES_UPLOAD_ADDR || 'localhost:50055',
  },
  useMocks: process.env.USE_MOCKS === 'true',   // all services fake
  mockServices: list('MOCK_SERVICES'),          // e.g. "catalog,auth" = only these are fake
  deadNodes: list('DEAD_NODES'),                // e.g. "catalog" = fake services that act dead
};