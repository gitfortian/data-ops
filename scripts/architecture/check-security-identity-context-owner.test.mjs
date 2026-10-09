import test from 'node:test';
import assert from 'node:assert/strict';
import { TYPES, readRepository, validateIdentityOwnership }
  from './check-security-identity-context-owner.mjs';

const OLD='data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const NEW='data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/';
const inspect=files=>validateIdentityOwnership(files).join('\n');

test('A8.2k-m repository owns exactly 11 stable auth, identity and model types in Platform',()=>{
  assert.equal(TYPES.length,11);
  assert.deepEqual(validateIdentityOwnership(readRepository()),[]);
});
test('A8.2k-m rejects resurrecting duplicate old AuthorizationSnapshot class',()=>{
  const files=readRepository(),p='context/AuthorizationSnapshot.java';
  files.set(OLD+p,files.get(NEW+p));
  assert.match(inspect(files),/duplicate legacy Security class/);
});
test('A8.2k-m rejects deleting AuthenticationManager from Platform',()=>{
  const files=readRepository();
  files.delete(NEW+'authentication/AuthenticationManager.java');
  assert.match(inspect(files),/missing Platform Security class/);
});
test('A8.2k-m rejects moving the historical request-context FQCN',()=>{
  const files=readRepository(),p=NEW+'context/YakSecurityContext.java';
  files.set(p,files.get(p).replace('package io.yak.framework.security.context;',
    'package io.yak.ops.platform.security.context;'));
  assert.match(inspect(files),/original Security type\/FQCN/);
});
test('A8.2k-m rejects loss of root or Project access logic',()=>{
  const files=readRepository(),p=NEW+'context/AuthorizationSnapshot.java';
  files.set(p,files.get(p).replace('SecurityPermissionCode.ROOT','SecurityPermissionCode.NONE'));
  assert.match(inspect(files),/snapshot immutability\/root/);
});
test('A8.2k-m rejects losing ThreadLocal cleanup',()=>{
  const files=readRepository(),p=NEW+'context/YakSecurityContext.java';
  files.set(p,files.get(p).replace('HOLDER.remove()','HOLDER.set(null)'));
  assert.match(inspect(files),/ThreadLocal cleanup/);
});
test('A8.2k-m prevents Sa-Token runtime import from leaking into Platform API',()=>{
  const files=readRepository(),p=NEW+'authentication/AuthenticationManager.java';
  files.set(p,files.get(p)+'\nimport cn.dev33.satoken.stp.StpLogic;\n');
  assert.match(inspect(files),/cannot import HTTP\/DB\/Sa-Token/);
});
test('A8.2k-m blocks losing custom AuthenticationManager logoutUser default',()=>{
  const files=readRepository(),p=NEW+'authentication/AuthenticationManager.java';
  files.set(p,files.get(p).replace('default void logoutUser(Long userId)', 'void logoutUser(Long userId)'));
  assert.match(inspect(files),/authentication implementation default ABI/);
});
test('A8.2k-m blocks moving the Servlet Filter into Platform',()=>{
  const files=readRepository(),p=OLD+'context/YakSecurityContextFilter.java';
  files.set(p,files.get(p).replace('YakSecurityContext.clear()', ''));
  assert.match(inspect(files),/must remain in Starter/);
});
test('A8.2k-m protects password masking after relocating User',()=>{
  const files=readRepository(),p=NEW+'common/entity/user/User.java';
  files.set(p,files.get(p).replaceAll('@ToString.Exclude',''));
  assert.match(inspect(files),/credential masking/);
});
test('A8.2k-m forbids new Platform to Security Starter Maven dependency',()=>{
  const files=readRepository(),p='data-ops-platform/data-ops-platform-security-contract/pom.xml';
  files.set(p,files.get(p).replace('<dependencies>',
    '<dependencies><dependency><artifactId>data-security-spring-boot-starter</artifactId></dependency>'));
  assert.match(inspect(files),/reverse depend/);
});
