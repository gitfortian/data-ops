/* CJS wrapper around the TS jest config: Node 24 cannot import the ESM-only
 * '@umijs/max/test' from a .ts jest config, so we require() it with an explicit
 * .js extension instead. Mirrors jest.config.ts exactly. */
const path = require('path');
const { configUmiAlias, createConfig } = require('@umijs/max/test');

async function buildConfig() {
  const config = await configUmiAlias({
    ...createConfig({
      target: 'browser',
    }),
  });
  return {
    ...config,
    rootDir: path.join(__dirname, '..'),
    testEnvironmentOptions: {
      ...(config?.testEnvironmentOptions || {}),
      url: 'http://localhost:8000',
    },
    setupFiles: [...(config.setupFiles || []), './tests/setupTests.jsx'],
    globals: {
      ...config.globals,
      localStorage: null,
    },
  };
}

module.exports = buildConfig;
