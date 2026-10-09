import test from 'node:test';
import assert from 'node:assert/strict';
import { PURE_SPI, RUNTIME_IMPLEMENTATIONS, MOVED_TESTS,
  readRepository, validateExtensionOwners } from './check-security-extension-owner.mjs';
const OLD='data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const CONTRACT='data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/';
const RUNTIME='data-ops-platform/data-ops-platform-security-runtime/src/main/java/io/yak/framework/security/';
const messages=(files)=>validateExtensionOwners(files).join('\n');

test('twelve Security extension classes and two tests have unique Platform owners',()=>{
  assert.equal(PURE_SPI.length,3);
  assert.equal(RUNTIME_IMPLEMENTATIONS.length,9);
  assert.equal(MOVED_TESTS.length,2);
  assert.deepEqual(validateExtensionOwners(readRepository()),[]);
});
test('rejects old duplicate PasswordEncoder SPI',()=>{
  const f=readRepository(),p='extend/PasswordEncoder.java';
  f.set(OLD+p,f.get(CONTRACT+p));
  assert.match(messages(f),/duplicate old Starter owner/);
});
test('rejects old duplicate BCrypt implementation',()=>{
  const f=readRepository(),p='extend/impl/DefaultPasswordEncoder.java';
  f.set(OLD+p,f.get(RUNTIME+p));
  assert.match(messages(f),/duplicate old Starter owner/);
});
test('rejects missing Platform extension SPI',()=>{
  const f=readRepository(); f.delete(CONTRACT+'extend/PermissionExtend.java');
  assert.match(messages(f),/missing Platform Security owner/);
});
test('rejects missing Runtime login guard',()=>{
  const f=readRepository(); f.delete(RUNTIME+'extend/impl/LoginAttemptGuard.java');
  assert.match(messages(f),/missing Platform Security owner/);
});
test('rejects legacy FQCN drift',()=>{
  const f=readRepository(),p=RUNTIME+'util/JsonUtils.java';
  f.set(p,f.get(p).replace('package io.yak.framework.security.util;', 'package io.yak.ops.other;'));
  assert.match(messages(f),/legacy ABI package/);
});
test('rejects pure SPI servlet runtime dependency',()=>{
  const f=readRepository(),p=CONTRACT+'extend/PasswordEncoder.java';
  f.set(p,f.get(p)+'\nimport jakarta.servlet.http.HttpServletRequest;\n');
  assert.match(messages(f),/pure Security SPI/);
});
test('rejects any old Framework DAO import in Runtime',()=>{
  const f=readRepository(),p=RUNTIME+'extend/impl/DefaultPermissionExtend.java';
  f.set(p,f.get(p)+'\nimport io.yak.framework.security.dao.UserDao;\n');
  assert.match(messages(f),/forbidden old Framework/);
});
test('rejects lost BCrypt hashing support',()=>{
  const f=readRepository(),p=RUNTIME+'extend/impl/DefaultPasswordEncoder.java';
  f.set(p,f.get(p).replaceAll('BCryptPasswordEncoder','BrokenEncoder'));
  assert.match(messages(f),/BCrypt hash matching/);
});
test('rejects request identity header bypass',()=>{
  const f=readRepository(),p=RUNTIME+'extend/impl/DefaultCurrentUserProvider.java';
  f.set(p,f.get(p).replace('authenticationManager.isLogin()','true'));
  assert.match(messages(f),/validated authentication/);
});
test('rejects default extra permission grant',()=>{
  const f=readRepository(),p=RUNTIME+'extend/impl/DefaultPermissionExtend.java';
  f.set(p,f.get(p).replace('return false;', 'return true;'));
  assert.match(messages(f),/deny-by-default/);
});
test('rejects missing Runtime test ownership',()=>{
  const f=readRepository();f.delete('data-ops-platform/data-ops-platform-security-runtime/src/test/java/io/yak/framework/security/extend/impl/LoginAttemptGuardTest.java');
  assert.match(messages(f),/missing Runtime regression test/);
});
test('rejects missing Jackson adapter dependency',()=>{
  const f=readRepository(),p='data-ops-platform/data-ops-platform-security-runtime/pom.xml';
  f.set(p,f.get(p).replace('<artifactId>jackson-databind</artifactId>','<artifactId>absent</artifactId>'));
  assert.match(messages(f),/Runtime dependency closure/);
});
test('rejects Runtime reverse Starter Maven dependency',()=>{
  const f=readRepository(),p='data-ops-platform/data-ops-platform-security-runtime/pom.xml';
  f.set(p,f.get(p).replace('</dependencies>','<dependency><artifactId>data-security-spring-boot-starter</artifactId></dependency></dependencies>'));
  assert.match(messages(f),/Runtime must not reverse/);
});
test('rejects removal of existing Spring auto-config beans',()=>{
  const f=readRepository(),p=OLD+'autoconfigure/YakSecurityAutoConfiguration.java';
  f.set(p,f.get(p).replace('new DefaultPasswordEncoder()','null'));
  assert.match(messages(f),/existing Spring default beans/);
});
test('rejects removal of login throttle from old verified login path',()=>{
  const f=readRepository(),p=OLD+'extend/impl/DefaultLoginExtendImpl.java';
  f.set(p,f.get(p).replace('new LoginAttemptGuard(loginProperties)','null'));
  assert.match(messages(f),/existing login flow/);
});
