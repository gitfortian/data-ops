// Keep Jest independent from the full Umi application config.
//
// `configUmiAlias()` parses config/config.ts before Jest starts. That couples unit tests to
// build-time plugins/OpenAPI/application config and makes focused tests fail before test
// discovery when the app config cannot be evaluated in the Jest bootstrap process.
// Data Development tests only need the stable source alias, so define it explicitly here.
import { createConfig } from '@umijs/max/test.js';

export default (): any => {
  const config = createConfig({
    target: 'browser',
  });
  return {
    ...config,
    moduleNameMapper: {
      ...(config.moduleNameMapper || {}),
      '^@/(.*)$': '<rootDir>/src/$1',
    },
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
};
