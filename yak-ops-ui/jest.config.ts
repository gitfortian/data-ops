// 显式 .js 扩展名：Node ESM 解析要求完整路径，否则 jest 无法加载本配置
// （修复前 `npx jest` 直接报 Cannot find module '@umijs/max/test'，全部测试跑不了）。
import { configUmiAlias, createConfig } from '@umijs/max/test.js';

export default async (): Promise<any> => {
  const config = await configUmiAlias({
    ...createConfig({
      target: 'browser',
    }),
  });
  return {
    ...config,
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
