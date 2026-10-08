import test from 'node:test';
import assert from 'node:assert/strict';
import { NAMES, NEW_DIR, OLD_DIR, readRepository, validateDeclarationOwnership }
  from './check-security-declaration-owner.mjs';

test('A8.2c repository has one source owner for all three runtime declarations', () => {
  assert.equal(NAMES.length, 3);
  assert.deepEqual(validateDeclarationOwnership(readRepository()), []);
});
test('A8.2c rejects resurrecting an old Framework copy', () => {
  const files = readRepository();
  files.set(OLD_DIR + 'YakPermission.java', files.get(NEW_DIR + 'YakPermission.java'));
  assert.match(validateDeclarationOwnership(files).join('\n'), /duplicate old owner/);
});
test('A8.2c rejects deleting the new source owner', () => {
  const files = readRepository();
  files.delete(NEW_DIR + 'PermissionDefinitionProvider.java');
  assert.match(validateDeclarationOwnership(files).join('\n'), /missing migrated ABI/);
});
test('A8.2c rejects changing the original annotation runtime retention', () => {
  const files = readRepository();
  const path = NEW_DIR + 'YakPermission.java';
  files.set(path, files.get(path).replace('RetentionPolicy.RUNTIME', 'RetentionPolicy.CLASS'));
  assert.match(validateDeclarationOwnership(files).join('\n'), /runtime retention/);
});
test('A8.2c rejects breaking package/FQCN under the new module', () => {
  const files = readRepository();
  const path = NEW_DIR + 'PermissionDefinition.java';
  files.set(path, files.get(path).replace(
    'package io.yak.framework.security.permission;',
    'package io.yak.ops.platform.security.permission;'));
  assert.match(validateDeclarationOwnership(files).join('\n'), /package\/FQCN/);
});
test('A8.2c rejects reversing dependency from Platform to Framework Starter', () => {
  const files = readRepository();
  const path = 'data-ops-platform/data-ops-platform-security-contract/pom.xml';
  files.set(path, files.get(path).replace('<dependencies>',
    '<dependencies><dependency><artifactId>data-security-spring-boot-starter</artifactId></dependency>'));
  assert.match(validateDeclarationOwnership(files).join('\n'), /depend back/);
});
test('A8.2c preserves the old permission scanning registration entry', () => {
  const files = readRepository();
  const path = OLD_DIR + 'PermissionRegistrationInitializer.java';
  files.set(path, files.get(path).replaceAll('YakPermission.class', 'DeprecatedPermission.class'));
  assert.match(validateDeclarationOwnership(files).join('\n'), /registration/);
});
