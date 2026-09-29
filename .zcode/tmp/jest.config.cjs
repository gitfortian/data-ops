// Temporary CJS mirror of data-ops-ui/jest.config.ts.
// Node 24 cannot ESM-import the extensionless '@umijs/max/test' subpath that
// the TS config uses, so this CJS copy (require resolves test.js) lets the
// navigation menu contract test run locally. Not part of the repo.
const maxTest = require('D:/tianxy/code/yak-ops/data-ops-ui/node_modules/@umijs/max/test.js');

module.exports = async () => {
  const config = await maxTest.configUmiAlias({
    ...maxTest.createConfig({
      target: 'browser',
    }),
  });
  return {
    ...config,
    rootDir: 'D:/tianxy/code/yak-ops/data-ops-ui',
    testEnvironmentOptions: {
      ...(config?.testEnvironmentOptions || {}),
      url: 'http://localhost:8000',
    },
    setupFiles: [...(config.setupFiles || []), '<rootDir>/tests/setupTests.jsx'],
    globals: {
      ...config.globals,
      localStorage: null,
    },
  };
};
