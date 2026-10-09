#!/usr/bin/env node
/**
 * A8.2q consolidated Security extension/runtime ownership gate.
 * Legacy FQCNs stay unchanged while unique compiled owners move from Starter.
 */
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const STARTER = 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const CONTRACT = 'data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/';
const RUNTIME = 'data-ops-platform/data-ops-platform-security-runtime/src/main/java/io/yak/framework/security/';
const TEST_OLD = 'data-ops-framework/data-security/src/test/java/io/yak/framework/security/';
const TEST_NEW = 'data-ops-platform/data-ops-platform-security-runtime/src/test/java/io/yak/framework/security/';

export const PURE_SPI = [
  'extend/PasswordEncoder.java',
  'extend/PermissionExtend.java',
  'extend/OperationLogExtend.java',
];
export const RUNTIME_IMPLEMENTATIONS = [
  'extend/CurrentUserProvider.java',
  'extend/impl/DefaultCurrentUserProvider.java',
  'extend/impl/DefaultPasswordEncoder.java',
  'extend/impl/DefaultPermissionExtend.java',
  'extend/impl/LoginAttemptGuard.java',
  'extend/impl/NoOpOperationLogExtend.java',
  'util/DatabaseNumberUtils.java',
  'util/JsonUtils.java',
  'util/MathUtil.java',
];
export const MOVED_TESTS = [
  'extend/impl/DefaultCurrentUserProviderTest.java',
  'extend/impl/LoginAttemptGuardTest.java',
];
const CONTRACT_POM = 'data-ops-platform/data-ops-platform-security-contract/pom.xml';
const RUNTIME_POM = 'data-ops-platform/data-ops-platform-security-runtime/pom.xml';
const STARTER_POM = 'data-ops-framework/data-security/pom.xml';
const STARTER_WIRING = STARTER + 'autoconfigure/YakSecurityAutoConfiguration.java';
const STARTER_LOGIN = STARTER + 'extend/impl/DefaultLoginExtendImpl.java';

export function readRepository(root=ROOT) {
  const paths=[
    ...PURE_SPI.flatMap(name=>[STARTER+name, CONTRACT+name]),
    ...RUNTIME_IMPLEMENTATIONS.flatMap(name=>[STARTER+name, RUNTIME+name]),
    ...MOVED_TESTS.flatMap(name=>[TEST_OLD+name, TEST_NEW+name]),
    CONTRACT_POM, RUNTIME_POM, STARTER_POM, STARTER_WIRING, STARTER_LOGIN
  ];
  const files=new Map();
  for(const path of paths) {
    const full=resolve(root,path);
    if(existsSync(full)) files.set(path,readFileSync(full,'utf8'));
  }
  return files;
}

export function validateExtensionOwners(files) {
  const errors=[];
  for(const [names,owner] of [[PURE_SPI,CONTRACT],[RUNTIME_IMPLEMENTATIONS,RUNTIME]]) {
    for(const path of names) {
      if(files.has(STARTER+path)) errors.push('duplicate old Starter owner: '+path);
      const source=files.get(owner+path);
      if(!source) {
        errors.push('missing Platform Security owner: '+path);
        continue;
      }
      const pkg='io.yak.framework.security.'+
        path.substring(0,path.lastIndexOf('/')).replaceAll('/','.');
      if(!source.includes('package '+pkg+';')) errors.push('legacy ABI package changed: '+path);
      if(/import\s+(?:io\.yak\.framework\.common|io\.yak\.framework\.security\.(?:dao|controller|service\.impl|autoconfigure)|io\.yak\.ops\.(?:common|business|boot))\b/.test(source)) {
        errors.push('forbidden old Framework or upper-layer dependency: '+path);
      }
      if(owner===CONTRACT && /^\s*import (?:jakarta|org\.springframework|cn\.dev33|com\.fasterxml)\b/m.test(source)) {
        errors.push('pure Security SPI must not depend on runtime libraries: '+path);
      }
    }
  }
  for(const path of MOVED_TESTS) {
    if(files.has(TEST_OLD+path)) errors.push('old Starter test owner remains: '+path);
    if(!files.has(TEST_NEW+path)) errors.push('missing Runtime regression test: '+path);
  }
  const encoder=files.get(RUNTIME+'extend/impl/DefaultPasswordEncoder.java')||'';
  if(!encoder.includes('BCryptPasswordEncoder') ||
     !encoder.includes('delegate.matches(')) errors.push('BCrypt hash matching compatibility changed');
  const current=files.get(RUNTIME+'extend/impl/DefaultCurrentUserProvider.java')||'';
  if(!current.includes('authenticationManager.isLogin()') ||
     !current.includes('authenticationManager.getLoginUsername()')) {
    errors.push('current user must come from validated authentication, not user headers');
  }
  const permission=files.get(RUNTIME+'extend/impl/DefaultPermissionExtend.java')||'';
  if(!permission.includes('return false;')) errors.push('deny-by-default permission changed');
  const limiter=files.get(RUNTIME+'extend/impl/LoginAttemptGuard.java')||'';
  if(!limiter.includes('ConcurrentHashMap') ||
     !limiter.includes('normalizeUsername(') ||
     !limiter.includes('recordFailure(')) errors.push('login failure local throttle compatibility changed');
  const json=files.get(RUNTIME+'util/JsonUtils.java')||'';
  if(!json.includes('ObjectMapper') || !json.includes('constructCollectionType')) {
    errors.push('Jackson legacy JSON contract changed');
  }
  const contractPom=files.get(CONTRACT_POM)||'';
  const runtimePom=files.get(RUNTIME_POM)||'';
  const starterPom=files.get(STARTER_POM)||'';
  if(/<artifactId>data-security-spring-boot-starter<\/artifactId>/.test(contractPom) ||
     /<artifactId>data-ops-(?:business|boot|common)[^<]*<\/artifactId>/.test(contractPom)) {
    errors.push('contract must not depend on old Starter or product upper layers');
  }
  if(!runtimePom.includes('<artifactId>data-ops-platform-security-contract</artifactId>') ||
     !runtimePom.includes('<artifactId>spring-security-crypto</artifactId>') ||
     !runtimePom.includes('<artifactId>jackson-databind</artifactId>')) {
    errors.push('Runtime dependency closure missing SPI, BCrypt or Jackson');
  }
  if(/<artifactId>data-security-spring-boot-starter<\/artifactId>/.test(runtimePom) ||
     /<artifactId>data-ops-(?:business|boot|common)[^<]*<\/artifactId>/.test(runtimePom)) {
    errors.push('Runtime must not reverse depend on old Starter or product layers');
  }
  if(!starterPom.includes('<artifactId>data-ops-platform-security-runtime</artifactId>') ||
     !starterPom.includes('<artifactId>data-ops-platform-security-contract</artifactId>')) {
    errors.push('Starter must depend on both unique Platform Security owners');
  }
  const wiring=files.get(STARTER_WIRING)||'';
  const login=files.get(STARTER_LOGIN)||'';
  if(!wiring.includes('new DefaultPasswordEncoder()') ||
     !wiring.includes('new DefaultPermissionExtend()') ||
     !wiring.includes('new NoOpOperationLogExtend()')) {
    errors.push('existing Spring default beans must retain implementations');
  }
  if(!login.includes('new LoginAttemptGuard(loginProperties)')) {
    errors.push('existing login flow must retain local failure limiting');
  }
  return errors;
}

if(process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  try {
    const errors=validateExtensionOwners(readRepository());
    if(errors.length) { console.error(errors.join('\n')); process.exitCode=1; }
    else console.log('A8.2q Security SPI, runtime implementation and Spring owner boundaries passed');
  } catch(error) {
    console.error('A8.2q failed closed: '+error.message);
    process.exitCode=1;
  }
}
