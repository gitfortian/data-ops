import test from 'node:test';
import assert from 'node:assert/strict';
import { SUPPORT_CLASSES, SUPPORT_TESTS, readSupportRepository, validateSupportOwners }
  from './check-security-runtime-owner.mjs';

const oldRoot = 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const newRoot = 'data-ops-platform/data-ops-platform-security-runtime/src/main/java/io/yak/framework/security/';
const fail = (sources) => validateSupportOwners(sources).join('\n');

test('A8.2o-p four support classes and four regressions have one runtime owner', () => {
  assert.equal(SUPPORT_CLASSES.length, 4);
  assert.equal(SUPPORT_TESTS.length, 4);
  assert.deepEqual(validateSupportOwners(readSupportRepository()), []);
});
test('A8.2o-p refuses a duplicate legacy properties owner', () => {
  const files = readSupportRepository(), name = SUPPORT_CLASSES[0];
  files.set(oldRoot + name, files.get(newRoot + name));
  assert.match(fail(files), /Duplicate Starter runtime owner/);
});
test('A8.2o-p refuses a missing HTTP Runtime class', () => {
  const files = readSupportRepository();
  files.delete(newRoot + 'util/HttpRequestUtil.java');
  assert.match(fail(files), /Missing Platform Runtime source/);
});
test('A8.2o-p refuses a changed legacy package', () => {
  const files = readSupportRepository(), path = newRoot + 'util/NetworkUtil.java';
  files.set(path, files.get(path).replace('package io.yak.framework.security.util;', 'package io.yak.other;'));
  assert.match(fail(files), /Runtime FQCN drift/);
});
test('A8.2o-p refuses Starter persistence imports', () => {
  const files = readSupportRepository(), path = newRoot + 'config/YakSecurityProperties.java';
  files.set(path, files.get(path) + '\nimport io.yak.framework.security.dao.UserDao;\n');
  assert.match(fail(files), /must not import Starter/);
});
test('A8.2o-p refuses an unowned regression test', () => {
  const files = readSupportRepository();
  files.delete('data-ops-platform/data-ops-platform-security-runtime/src/test/java/io/yak/framework/security/util/NetworkUtilTest.java');
  assert.match(fail(files), /Runtime regression test missing/);
});
test('A8.2o-p refuses property prefix drift', () => {
  const files = readSupportRepository(), path = newRoot + 'config/YakSecurityProperties.java';
  files.set(path, files.get(path).replace('PREFIX = "yak.security"', 'PREFIX = "other.security"'));
  assert.match(fail(files), /Security property ABI drift/);
});
test('A8.2o-p refuses project-header changes', () => {
  const files = readSupportRepository(), path = newRoot + 'util/HttpRequestUtil.java';
  files.set(path, files.get(path).replace('X-YAK-SECURITY-PROJECT-ID', 'X-OTHER'));
  assert.match(fail(files), /Project request identity/);
});
test('A8.2o-p refuses proxy-header changes', () => {
  const files = readSupportRepository(), path = newRoot + 'util/NetworkUtil.java';
  files.set(path, files.get(path).replace('X-Forwarded-For', 'Other'));
  assert.match(fail(files), /Audit IP source/);
});
test('A8.2o-p refuses loss of audit token redaction', () => {
  const files = readSupportRepository(), path = newRoot + 'util/SensitiveDataSanitizer.java';
  files.set(path, files.get(path).replaceAll('[REDACTED]', '[VISIBLE]'));
  assert.match(fail(files), /Audit secret redaction/);
});
test('A8.2o-p refuses reverse Starter Maven dependencies', () => {
  const files = readSupportRepository(), path = 'data-ops-platform/data-ops-platform-security-runtime/pom.xml';
  files.set(path, files.get(path).replace('</dependencies>',
    '<dependency><artifactId>data-security-spring-boot-starter</artifactId></dependency></dependencies>'));
  assert.match(fail(files), /forbidden reverse Maven dependency/);
});
test('A8.2o-p refuses missing servlet dependency', () => {
  const files = readSupportRepository(), path = 'data-ops-platform/data-ops-platform-security-runtime/pom.xml';
  files.set(path, files.get(path).replace('<artifactId>jakarta.servlet-api</artifactId>',
    '<artifactId>missing-servlet-api</artifactId>'));
  assert.match(fail(files), /Runtime dependency missing: jakarta.servlet-api/);
});
