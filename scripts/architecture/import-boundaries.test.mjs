import test from 'node:test';
import assert from 'node:assert/strict';
import { crossesPersistenceBoundary } from './import-boundaries.mjs';
const source = { module: 'data-ops-business-sync-offline' };
const classes = new Map([['io.yak.ops.business.datasource.dao.DataSourceDao', { module: 'data-ops-business-datasource' }],
  ['io.yak.ops.business.sync.offline.dao.DefinitionDao', source]]);
test('explicit, wildcard and static persistence imports cannot bypass domain ownership', () => {
  for (const name of ['io.yak.ops.business.datasource.dao.DataSourceDao', 'io.yak.ops.business.datasource.dao.*',
    'io.yak.ops.business.datasource.dao.DataSourceDao.PageQuery', 'io.yak.ops.business.datasource.dao.DataSourceDao.*'])
    assert.equal(crossesPersistenceBoundary(source, name, classes), true, name);
  assert.equal(crossesPersistenceBoundary(source, 'io.yak.ops.business.sync.offline.dao.*', classes), false);
  assert.equal(crossesPersistenceBoundary({ module: 'data-ops-boot' }, 'io.yak.ops.business.datasource.dao.*', classes), false);
});
