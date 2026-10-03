// Keep Jest independent from the full Umi application config.
//
// `configUmiAlias()` parses config/config.ts before Jest starts. That couples unit tests to
// build-time plugins/OpenAPI/application config and makes focused tests fail before test
// discovery when the app config cannot be evaluated in the Jest bootstrap process.
// Data Development tests only need the stable source alias and a small runtime history shim,
// so define both explicitly here instead of loading Umi's build-time CLI entry in jsdom.
import { createConfig } from '@umijs/max/test.js';

export default (): any => {
  const config = createConfig({
    target: 'browser',
  });
  return {
    ...config,
    transform: {
      '^.+\\.[cm]?[jt]sx?$': '<rootDir>/tests/transform.cjs',
    },
    transformIgnorePatterns: ['/node_modules/(?!marked/)'],
    moduleNameMapper: {
      ...(config.moduleNameMapper || {}),
      '\\.(webp|png|gif|jpe?g|svg)$': '<rootDir>/tests/mocks/file.cjs',
      '^@/(.*)$': '<rootDir>/src/$1',
      '^umi$': '<rootDir>/tests/mocks/umi.ts',
    },
    testEnvironmentOptions: {
      ...(config?.testEnvironmentOptions || {}),
      url: 'http://localhost:8000',
    },
    setupFiles: [...(config.setupFiles || []), './tests/setupTests.jsx'],
    setupFilesAfterEnv: [...(config.setupFilesAfterEnv || []), '@testing-library/jest-dom'],
    globals: {
      ...config.globals,
      localStorage: null,
    },
  };
};
