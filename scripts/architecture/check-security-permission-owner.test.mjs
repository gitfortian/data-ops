import test from 'node:test';
import assert from 'node:assert/strict';
import { readRepository, validatePermissionOwner } from './check-security-permission-owner.mjs';

const legacy = 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/common/constant/SecurityPermissionCode.java';
const owner = 'data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/ops/platform/security/contract/SecurityPermissionCode.java';
const project = 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/controller/v1/ProjectController.java';

test('A8.2b actual checkout has one authoritative contract and legacy forwarding facade', () => {
  assert.deepEqual(validatePermissionOwner(readRepository()), []);
});
test('A8.2b refuses to remove platform module from reactor', () => {
  const files = readRepository();
  files['pom.xml'] = files['pom.xml'].replace(
    '<module>data-ops-platform/data-ops-platform-security-contract</module>', '');
  assert.match(validatePermissionOwner(files).join('\n'), /root reactor/);
});
test('A8.2b prevents a second legacy permission value owner', () => {
  const files = readRepository();
  files[legacy] = files[legacy].replace(
    'io.yak.ops.platform.security.contract.SecurityPermissionCode.Project.READ',
    '"security:project:read"');
  assert.match(validatePermissionOwner(files).join('\n'), /legacy field must forward/);
});
test('A8.2b prevents silently dropping a permission from the canonical owner', () => {
  const files = readRepository();
  files[owner] = files[owner].replace(
    'public static final String READ = "security:project:read";',
    'public static final String CHANGED = "security:project:read";');
  assert.match(validatePermissionOwner(files).join('\n'), /legacy field must forward|unknown legacy field/);
});
test('A8.2b prohibits backward dependency from Platform to Framework starter', () => {
  const files = readRepository();
  files['data-ops-platform/data-ops-platform-security-contract/pom.xml'] =
      files['data-ops-platform/data-ops-platform-security-contract/pom.xml'].replace(
        '<dependencies>', '<dependencies><dependency><artifactId>data-security-spring-boot-starter</artifactId></dependency>');
  assert.match(validatePermissionOwner(files).join('\n'), /independent/);
});
test('A8.2b refuses controller reverting to old permission contract', () => {
  const files = readRepository();
  files[project] = files[project].replace(
    'import io.yak.ops.platform.security.contract.SecurityPermissionCode;',
    'import io.yak.framework.security.common.constant.SecurityPermissionCode;');
  assert.match(validatePermissionOwner(files).join('\n'), /ProjectController/);
});
