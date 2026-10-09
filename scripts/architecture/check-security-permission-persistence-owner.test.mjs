import test from 'node:test';
import assert from 'node:assert/strict';
import { FILES, checkPersistencePortOwnership, readRepository }
  from './check-security-permission-persistence-owner.mjs';

test('A8.2e checkout moves both source owners and preserves old DAO implementation', () => {
  assert.deepEqual(checkPersistencePortOwnership(readRepository()), []);
});
test('A8.2e rejects duplicate Framework Permission model owner', () => {
  const files = readRepository();
  files.set(FILES.oldModel, files.get(FILES.newModel));
  assert.match(checkPersistencePortOwnership(files).join('\n'), /duplicate Permission/);
});
test('A8.2e rejects duplicate Framework PermissionDao class owner', () => {
  const files = readRepository();
  files.set(FILES.oldDao, files.get(FILES.newDao));
  assert.match(checkPersistencePortOwnership(files).join('\n'), /duplicate Permission/);
});
test('A8.2e rejects moving the public DAO package/FQCN', () => {
  const files = readRepository();
  files.set(FILES.newDao, files.get(FILES.newDao).replace(
    'package io.yak.framework.security.dao;', 'package io.yak.ops.platform.security.dao;'));
  assert.match(checkPersistencePortOwnership(files).join('\n'), /DAO legacy FQCN/);
});
test('A8.2e rejects losing parent code transient field', () => {
  const files = readRepository();
  files.set(FILES.newModel, files.get(FILES.newModel)
    .replace('private transient String parentCode;', 'private String parentCode;'));
  assert.match(checkPersistencePortOwnership(files).join('\n'), /transient ABI/);
});
test('A8.2e rejects loss of soft deactivation for previously declared permissions', () => {
  const files = readRepository();
  files.set(FILES.implementation, files.get(FILES.implementation)
    .replace('Boolean.TRUE.equals(item.getDeclared())', 'false'));
  assert.match(checkPersistencePortOwnership(files).join('\n'), /MyBatis CRUD/);
});
test('A8.2e rejects removal of parent ID population', () => {
  const files = readRepository();
  files.set(FILES.implementation, files.get(FILES.implementation)
    .replace('row.setParentId(parent.getId())', 'row.setParentId(null)'));
  assert.match(checkPersistencePortOwnership(files).join('\n'), /MyBatis CRUD/);
});
test('A8.2e rejects lower Platform reverse dependency on Starter', () => {
  const files = readRepository();
  files.set(FILES.platformPom, files.get(FILES.platformPom)
    .replace('<dependencies>',
      '<dependencies><dependency><artifactId>data-security-spring-boot-starter</artifactId></dependency>'));
  assert.match(checkPersistencePortOwnership(files).join('\n'), /must not depend/);
});
