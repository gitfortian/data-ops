import test from 'node:test';
import assert from 'node:assert/strict';
import { checkSecurityContract, readSecurityFiles } from './check-security-migration-contract.mjs';

test('A8.2a verified repository retains security boot and database contracts', () => {
  assert.deepEqual(checkSecurityContract(readSecurityFiles()), []);
});
test('A8.2a rejects two auto-configuration registrations', () => {
  const files = readSecurityFiles();
  files.imports += '\nio.yak.ops.security.autoconfigure.AnotherSecurityAutoConfiguration\n';
  assert.match(checkSecurityContract(files).join('\n'), /exactly one/);
});
test('A8.2a rejects missing authentication default', () => {
  const files = readSecurityFiles();
  files.properties = files.properties.replace('private boolean authenticationEnabled = true',
    'private boolean authenticationEnabled = false');
  assert.match(checkSecurityContract(files).join('\n'), /authentication default/);
});
test('A8.2a rejects losing independent datasource', () => {
  const files = readSecurityFiles();
  files.datasource = files.datasource.replace('"yakSecurityDataSource"', '"dataSource"');
  assert.match(checkSecurityContract(files).join('\n'), /independent Security datasource/);
});
test('A8.2a rejects silent PostgreSQL Project schema removal', () => {
  const files = readSecurityFiles();
  files.postgres = files.postgres.replace('CREATE TABLE "yak_security_project"',
    'CREATE TABLE "deprecated_project"');
  assert.match(checkSecurityContract(files).join('\n'), /PostgreSQL Project history/);
});
test('A8.2a rejects disabled tenant isolation interceptor', () => {
  const files = readSecurityFiles();
  files.datasource = files.datasource.replaceAll('TenantLineInnerInterceptor', 'RemovedTenantInterceptor');
  assert.match(checkSecurityContract(files).join('\n'), /tenant isolation/);
});
