import test from 'node:test';
import assert from 'node:assert/strict';
import { importedFrameworkRequest, checkServiceTransportImports } from './frontend-service-transport.mjs';

const service = 'data-ops-ui/src/services/metric/api.ts';

test('multiline Umi request import is rejected with a precise source line', () => {
  const source = [
    "import type { RequestConfig } from '@umijs/max';",
    "import {",
    "  history,",
    "  request as sendRequest,",
    "  getLocale,",
    "} from '@umijs/max';",
  ].join('\n');
  assert.deepEqual(importedFrameworkRequest(source), [{ line: 2, module: '@umijs/max' }]);
  assert.match(checkServiceTransportImports(service, source)[0], /metric\/api\.ts:2:/);
});

test('Umi direct request cannot hide behind a named or default alias', () => {
  assert.equal(importedFrameworkRequest("import {request} from 'umi';").length, 1);
  assert.equal(importedFrameworkRequest("import request from '@umijs/max';").length, 1);
  assert.equal(importedFrameworkRequest("import { request as http, history } from '@umijs/max';").length, 1);
});

test('only real runtime request imports from Umi violate the transport boundary', () => {
  for (const source of [
    "import { history, useModel } from '@umijs/max';",
    "import { type request, history } from '@umijs/max';",
    "import type { request } from '@umijs/max';",
    "import request from '@/utils/request';",
    "import HttpUtils from '@/utils/HttpUtils';",
    "// import { request } from '@umijs/max';",
    "const request = () => fetch('/api/v1/ping');",
    "import { request } from '@/services/metric/api';",
    "import { history } from '@umijs/max';\nimport { something } from 'other';",
  ]) {
    assert.deepEqual(importedFrameworkRequest(source), [], source);
  }
});

test('guard applies only to production domain services, not app bootstrap or tests', () => {
  const bypass = "import { request } from '@umijs/max';";
  assert.equal(checkServiceTransportImports(service, bypass).length, 1);
  assert.deepEqual(checkServiceTransportImports('data-ops-ui/src/app.tsx', bypass), []);
  assert.deepEqual(checkServiceTransportImports('data-ops-ui/src/services/metric/api.test.ts', bypass), []);
  assert.deepEqual(checkServiceTransportImports('data-ops-ui/src/pages/metric/index.tsx', bypass), []);
});
