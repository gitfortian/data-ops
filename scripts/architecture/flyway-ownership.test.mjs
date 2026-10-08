import test from 'node:test';
import assert from 'node:assert/strict';
import { parseFlywayRegistrations, analyzeFlywayOwnership } from '../db/flyway-ownership.mjs';

const owner = 'data-ops-business/data-ops-business-metric';
const configPath = owner + '/src/main/java/io/yak/ops/business/metric/config/MetricPersistenceConfiguration.java';
const resource = (vendor, namespace) => owner + '/src/main/resources/db/' + vendor + '/' + namespace + '/V1__init.sql';

function config(namespace = 'yak-metric', history = 'flyway_schema_history_metric') {
  return [
    'package io.yak.ops.business.metric.config;',
    'import org.flywaydb.core.Flyway;',
    'public class MetricPersistenceConfiguration {',
    '  private static final String TABLE = "' + history + '";',
    '  private static final String LOCATION = "classpath:db/migration/' + namespace + '";',
    '  @Bean(name = "yakMetricFlyway", initMethod = "migrate")',
    '  public Flyway metricFlyway(DataSource dataSource) {',
    '    return Flyway.configure().dataSource(dataSource)',
    '        .locations(JdbcDatabase.migrationLocation(dataSource, LOCATION))',
    '        .table(TABLE).baselineOnMigrate(true).load();',
    '  }',
    '}',
  ].join('\n');
}

test('static Flyway registration resolves both final String constants and declared Bean name', () => {
  const items = parseFlywayRegistrations(configPath, config());
  assert.equal(items.length, 1);
  assert.equal(items[0].table, 'flyway_schema_history_metric');
  assert.equal(items[0].location, 'classpath:db/migration/yak-metric');
  assert.equal(items[0].beanName, 'yakMetricFlyway');
  assert.equal(items[0].owner, owner);
});

test('valid module-owned Flyway chain has independent history and two local vendor script sets', () => {
  const files = [resource('migration', 'yak-metric'), resource('migration-postgresql', 'yak-metric')];
  const result = analyzeFlywayOwnership([{ file: configPath, content: config() }], files);
  assert.deepEqual(result.violations, []);
});

test('history table and migration namespace cannot be claimed by multiple modules', () => {
  const secondOwner = 'data-ops-business/data-ops-business-asset';
  const secondFile = secondOwner + '/src/main/java/io/yak/ops/business/asset/config/AssetPersistenceConfiguration.java';
  const secondConfig = config().replaceAll('MetricPersistenceConfiguration', 'AssetPersistenceConfiguration')
    .replaceAll('yakMetricFlyway', 'yakAssetFlyway');
  const files = [
    resource('migration', 'yak-metric'),
    resource('migration-postgresql', 'yak-metric'),
    secondOwner + '/src/main/resources/db/migration/yak-metric/V1__init.sql',
    secondOwner + '/src/main/resources/db/migration-postgresql/yak-metric/V1__init.sql',
  ];
  const audit = analyzeFlywayOwnership([
    { file: configPath, content: config() },
    { file: secondFile, content: secondConfig },
  ], files);
  assert.match(audit.violations.join('\n'), /duplicate history table flyway_schema_history_metric/);
  assert.match(audit.violations.join('\n'), /duplicate migration namespace classpath:db\/migration\/yak-metric/);
});

test('a module cannot reference SQL only present in a different module', () => {
  const result = analyzeFlywayOwnership([{ file: configPath, content: config() }], [
    'data-ops-business/data-ops-business-asset/src/main/resources/db/migration/yak-metric/V1.sql',
    'data-ops-business/data-ops-business-asset/src/main/resources/db/migration-postgresql/yak-metric/V1.sql',
  ]);
  assert.equal(result.violations.length, 2);
  assert.ok(result.violations.every((message) => message.includes('missing local')));
});

test('one missing PostgreSQL mirror and unreviewed dynamic identifiers fail closed', () => {
  const missing = analyzeFlywayOwnership([{ file: configPath, content: config() }],
    [resource('migration', 'yak-metric')]);
  assert.match(missing.violations.join('\n'), /migration-postgresql/);
  const dynamic = config().replace('.table(TABLE)', '.table(historyNameSupplier.get())');
  const unresolved = analyzeFlywayOwnership([{ file: configPath, content: dynamic }],
    [resource('migration', 'yak-metric'), resource('migration-postgresql', 'yak-metric')]);
  assert.match(unresolved.violations.join('\n'), /dynamic ownership needs explicit review/);
});

test('non-reactor legacy and non-Bean helpers cannot register a production migration owner', () => {
  assert.equal(parseFlywayRegistrations(
    'data-ops-framework/legacy/src/main/java/Demo.java', config()).length, 0);
  assert.equal(parseFlywayRegistrations(configPath, config().replace('@Bean(name = "yakMetricFlyway", initMethod = "migrate")', '')).length, 0);
});

test('independently sourced Framework Security migrations retain their own lifecycle', () => {
  const frameworkConfig = 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/config/DataSourceConfig.java';
  const security = config().replaceAll('MetricPersistenceConfiguration', 'DataSourceConfig')
    .replace('.table(TABLE)', '.locations(FLYWAY_MIGRATION_LOCATION).outOfOrder(true)');
  assert.deepEqual(parseFlywayRegistrations(frameworkConfig, security), []);
  const audit = analyzeFlywayOwnership([{ file: frameworkConfig, content: security }], []);
  assert.deepEqual(audit.violations, []);
  assert.equal(audit.registrations.length, 0);
});
