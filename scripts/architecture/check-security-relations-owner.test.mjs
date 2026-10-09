import test from 'node:test';
import assert from 'node:assert/strict';
import { MIGRATED, RELATED, readRepository, validateRelations }
  from './check-security-relations-owner.mjs';

const OLD = 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const NEW = 'data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/';

test('A8.2f all four Project/RBAC types have only one Platform owner', () => {
  assert.equal(MIGRATED.length, 4);
  assert.deepEqual(validateRelations(readRepository()), []);
});
test('A8.2f rejects resurrecting original Framework RBAC entity', () => {
  const files = readRepository();
  files.set(OLD + 'common/entity/RolePermission.java',
    files.get(NEW + 'common/entity/RolePermission.java'));
  assert.match(validateRelations(files).join('\n'), /duplicate Framework/);
});
test('A8.2f fails closed when a new Platform port goes missing', () => {
  const files = readRepository();
  files.delete(NEW + 'dao/RolePermissionDao.java');
  assert.match(validateRelations(files).join('\n'), /missing Platform/);
});
test('A8.2f rejects changing Project membership userType dimension', () => {
  const files = readRepository();
  const path = NEW + 'common/entity/UserProject.java';
  files.set(path, files.get(path).replace('private Integer userType;', 'private Integer tenantRole;'));
  assert.match(validateRelations(files).join('\n'), /Project membership identity/);
});
test('A8.2f rejects dropping UserRole all-arguments constructor', () => {
  const files = readRepository();
  const path = NEW + 'common/entity/UserRole.java';
  files.set(path, files.get(path).replace('@AllArgsConstructor', ''));
  assert.match(validateRelations(files).join('\n'), /constructor ABI/);
});
test('A8.2f rejects removing RolePermission deleteByPermissionId', () => {
  const files = readRepository();
  const path = NEW + 'dao/RolePermissionDao.java';
  files.set(path, files.get(path).replace(
    'void deleteByPermissionId(Long permissionId)', 'void deleteAny(Long permissionId)'));
  assert.match(validateRelations(files).join('\n'), /public persistence contract/);
});
test('A8.2f refuses Platform reverse dependency on old Security starter', () => {
  const files = readRepository();
  files.set(RELATED.platformPom,
    files.get(RELATED.platformPom).replace('<dependencies>',
      '<dependencies><dependency><artifactId>data-security-spring-boot-starter</artifactId></dependency>'));
  assert.match(validateRelations(files).join('\n'), /reverse dependencies/);
});
test('A8.2f detects loss of role-cache invalidation', () => {
  const files = readRepository();
  files.set(RELATED.service,
    files.get(RELATED.service).replaceAll('permissionCache.invalidateRole(roleId)', ''));
  assert.match(validateRelations(files).join('\n'), /transaction\/cache invalidation/);
});
test('A8.2f retains DTO/PO-dependent Project and UserRole DAOs in old starter', () => {
  const files = readRepository();
  files.delete(RELATED.userProjectDao);
  assert.match(validateRelations(files).join('\n'), /PO\/DTO-dependent/);
});
