import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { findPersistenceConsumers, checkDeclaredPersistenceAliases, persistenceInventory, PERSISTENCE_ALIASES } from './persistence-consumers.mjs';

const boot = 'data-ops-boot/src/main/java/io/yak/ops/boot/config/persistence/BusinessDatabaseConfiguration.java';
const moduleFile = 'data-ops-business/data-ops-business-metric/src/main/java/MetricPersistenceConfiguration.java';

test('known shared bean aliases stay grouped by DataSource/Tx/Factory/Template', () => {
  assert.equal(Object.keys(PERSISTENCE_ALIASES).length, 4);
  assert.equal(new Set(Object.values(PERSISTENCE_ALIASES).flat()).size, 16);
});

test('captures actual Spring qualifiers, MapperScan, getBean, DependsOn, Resource with line evidence', () => {
  const code = [
    'class Example {',
    '  @Qualifier("yakBusinessDataSource") DataSource source;',
    '  @MapperScan(sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")',
    '  @Resource(name = "offlineSyncSqlSessionTemplate") Object sessions;',
    '  @DependsOn("yakBusinessTransactionManager")',
    '  void context() { ctx.getBean("opsDataSource"); }',
    '}',
  ].join('\n');
  assert.deepEqual(findPersistenceConsumers(moduleFile, code).map(x => [x.line, x.bean]), [
    [2, 'yakBusinessDataSource'], [3, 'yakBusinessSqlSessionFactory'],
    [4, 'offlineSyncSqlSessionTemplate'], [5, 'yakBusinessTransactionManager'],
    [6, 'opsDataSource'],
  ]);
});

test('ignores unknown beans, legacy framework, app tests and non-production code', () => {
  const code = '@Qualifier("unknown")\n@Qualifier("yakBusinessDataSource")';
  assert.equal(findPersistenceConsumers(moduleFile, code).length, 1);
  assert.deepEqual(findPersistenceConsumers('data-ops-framework/legacy/src/main/java/Test.java', code), []);
  assert.deepEqual(findPersistenceConsumers('data-ops-boot/src/test/java/Test.java', code), []);
});

test('detects missing or duplicate declared Boot aliases, ignores @Qualifier reference count', () => {
  const declared = [
    '@Bean(name = {"yakBusinessDataSource", "opsDataSource"})',
    'public DataSource datasource() {}',
    '@Qualifier("yakBusinessDataSource")',
    '@Bean("opsDataSource") public DataSource duplicate() {}',
  ].join('\n');
  const result = checkDeclaredPersistenceAliases([{file: boot, content: declared}]);
  assert.ok(result.missing.some(x => x.bean === 'offlineSyncDataSource'));
  assert.ok(result.duplicate.some(x => x.bean === 'opsDataSource'));
  assert.ok(!result.duplicate.some(x => x.bean === 'yakBusinessDataSource'));
});

test('main actual Boot alias declarations are present; inventory never requires a specific consumer count', () => {
  const source = readFileSync(boot, 'utf8');
  // A2.1 splits MyBatis declarations into a separate @Import configuration.
  // Independently, A2.2 must validate *all* Boot-owned Bean declarations,
  // while still passing on main before A2.1 has been merged.
  const mybatis = 'data-ops-boot/src/main/java/io/yak/ops/boot/config/persistence/BusinessMybatisSessionConfiguration.java';
  const result = persistenceInventory([
    { file: boot, content: source },
    ...(existsSync(mybatis) ? [{ file: mybatis, content: readFileSync(mybatis, 'utf8') }] : []),
    { file: moduleFile, content: '@MapperScan(sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")' },
  ]);
  assert.deepEqual(result.declared, {missing: [], duplicate: []});
  assert.equal(result.consumers.some(x => x.bean === 'yakBusinessSqlSessionFactory'), true);
  assert.equal(result.modules.some(x => x.module === 'data-ops-business/data-ops-business-metric'), true);
});
