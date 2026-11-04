const config = require('../config');
const mock = require('./mock');

const names = ['auth', 'catalog', 'enrollment', 'gradesView', 'gradesUpload'];
const useMock = (n) => config.useMocks || config.mockServices.includes(n);

for (const n of names) {
  Object.defineProperty(module.exports, n, {
    enumerable: true,
    get: () => (useMock(n) ? mock[n] : require('./clients')[n]),
  });
}