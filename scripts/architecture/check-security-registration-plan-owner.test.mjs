import test from 'node:test';
import assert from 'node:assert/strict';
import { checkPlannerOwnership, readRepository }
  from './check-security-registration-plan-owner.mjs';

test('A8.2d product repository uses one Platform owner for permission planning', () => {
  assert.deepEqual(checkPlannerOwnership(readRepository()), []);
});
test('A8.2d rejects moving reconciliation back into the Security Starter', () => {
  const files = readRepository();
  files.service = files.service.replace('PermissionDeclarationPlan.from(definitions).entries()',
    'legacyPlan(definitions)');
  assert.match(checkPlannerOwnership(files).join('\n'), /delegate planning/);
});
test('A8.2d rejects losing the dedicated Security transaction manager', () => {
  const files = readRepository();
  files.service = files.service.replace('yakSecurityTransactionManager', 'transactionManager');
  assert.match(checkPlannerOwnership(files).join('\n'), /preserve transaction/);
});
test('A8.2d refuses Platform depending on Framework DAO or database entities', () => {
  const files = readRepository();
  files.plan += '\nimport io.yak.framework.security.dao.PermissionDao;\n';
  assert.match(checkPlannerOwnership(files).join('\n'), /persistence and Spring transaction free/);
});
test('A8.2d forbids Framework Security dependency in lower-layer pom', () => {
  const files = readRepository();
  files.contractPom = files.contractPom.replace('<dependencies>', 
    '<dependencies><dependency><artifactId>data-security-spring-boot-starter</artifactId></dependency>');
  assert.match(checkPlannerOwnership(files).join('\n'), /reverse-depend/);
});
test('A8.2d detects loss of first-wins duplicate behavior', () => {
  const files = readRepository();
  files.plan = files.plan.replace('putIfAbsent', 'put');
  assert.match(checkPlannerOwnership(files).join('\n'), /first-wins/);
});
test('A8.2d detects Project parent reference loss during DAO mapping', () => {
  const files = readRepository();
  files.service = files.service.replace('permission.setParentCode(entry.parentCode());',
    'permission.setParentCode(null);');
  assert.match(checkPlannerOwnership(files).join('\n'), /Project parent/);
});
