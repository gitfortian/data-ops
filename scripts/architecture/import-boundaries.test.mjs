import test from 'node:test';
import assert from 'node:assert/strict';
import { crossesPersistenceBoundary, dependsOnBootOutsideAssembly, importsBootOutsideBoot } from './import-boundaries.mjs';
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

test('Boot is the composition root; only distribution may depend on its artifact', () => {
  assert.equal(dependsOnBootOutsideAssembly('data-ops-business-asset', ['data-ops-boot']), true);
  assert.equal(dependsOnBootOutsideAssembly('data-ops-core', ['data-ops-boot']), true);
  assert.equal(dependsOnBootOutsideAssembly('data-ops-core', ['data-ops-spi']), false);
  assert.equal(dependsOnBootOutsideAssembly('data-ops-dist', ['data-ops-boot']), false);
  assert.equal(dependsOnBootOutsideAssembly('data-ops-boot', ['data-ops-core']), false);
});

test('Non-Boot modules cannot import Boot internals, including wildcard and static imports', () => {
  const business = { module: 'data-ops-business-asset' };
  const framework = { module: 'data-ops-core' };
  const boot = { module: 'data-ops-boot' };
  for (const name of ['io.yak.ops.boot.config.SomeConfiguration',
    'io.yak.ops.boot.config.*',
    'io.yak.ops.boot.config.SomeConfiguration.VALUE']) {
    assert.equal(importsBootOutsideBoot(business, name), true, name);
    assert.equal(importsBootOutsideBoot(framework, name), true, name);
    assert.equal(importsBootOutsideBoot(boot, name), false, name);
  }
  assert.equal(importsBootOutsideBoot(business, 'io.yak.ops.business.asset.api.AssetCatalogApi'), false);
  assert.equal(importsBootOutsideBoot(business, 'io.yak.ops.bootstrap.AnotherModule'), false);
});
