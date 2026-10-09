// A8.2n: forbid accidental duplicate Sa-Token adapter ownership.
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const oldPath = resolve(root, 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/authentication/SaTokenAuthenticationManager.java');
const newPath = resolve(root, 'data-ops-platform/data-ops-platform-security-runtime/src/main/java/io/yak/framework/security/authentication/SaTokenAuthenticationManager.java');
const oldTest = resolve(root, 'data-ops-framework/data-security/src/test/java/io/yak/framework/security/authentication/SaTokenAuthenticationManagerTest.java');
const newTest = resolve(root, 'data-ops-platform/data-ops-platform-security-runtime/src/test/java/io/yak/framework/security/authentication/SaTokenAuthenticationManagerTest.java');
if (existsSync(oldTest) || !existsSync(newTest)) throw Error('Sa-Token adapter regression tests must belong to Runtime');
const rootPom = readFileSync(resolve(root, 'pom.xml'), 'utf8');
const starter = readFileSync(resolve(root, 'data-ops-framework/data-security/pom.xml'), 'utf8');
const runtime = readFileSync(resolve(root, 'data-ops-platform/data-ops-platform-security-runtime/pom.xml'), 'utf8');
if (existsSync(oldPath)) throw Error('Duplicate legacy SaTokenAuthenticationManager owner');
if (!existsSync(newPath)) throw Error('Missing Platform Runtime SaTokenAuthenticationManager');
const java = readFileSync(newPath, 'utf8');
if (!java.includes('package io.yak.framework.security.authentication;') || !java.includes('implements AuthenticationManager')) throw Error('Sa-Token authentication ABI changed');
if (!rootPom.includes('<module>data-ops-platform/data-ops-platform-security-runtime</module>')) throw Error('Runtime absent from reactor');
if (!starter.includes('<artifactId>data-ops-platform-security-runtime</artifactId>')) throw Error('Starter does not consume runtime');
if (!runtime.includes('<artifactId>data-ops-platform-security-contract</artifactId>')) throw Error('Runtime does not depend on canonical contract');
if (/data-ops-(?:business|boot|common)/.test(runtime)) throw Error('Runtime must not depend on product layers');
console.log('A8.2n Sa-Token Runtime owner checks passed');


// A8.2o–p: all supporting runtime classes retain one binary and test owner.
export const SUPPORT_CLASSES = [
  'config/YakSecurityProperties.java',
  'util/HttpRequestUtil.java',
  'util/NetworkUtil.java',
  'util/SensitiveDataSanitizer.java'
];
export const SUPPORT_TESTS = [
  'config/YakSecurityPropertiesTest.java',
  'util/HttpRequestUtilTest.java',
  'util/NetworkUtilTest.java',
  'util/SensitiveDataSanitizerTest.java'
];
const starterJava = (name) => 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/' + name;
const runtimeJava = (name) => 'data-ops-platform/data-ops-platform-security-runtime/src/main/java/io/yak/framework/security/' + name;
const starterTest = (name) => 'data-ops-framework/data-security/src/test/java/io/yak/framework/security/' + name;
const runtimeTest = (name) => 'data-ops-platform/data-ops-platform-security-runtime/src/test/java/io/yak/framework/security/' + name;

export function readSupportRepository(rootDir = root) {
  const sources = new Map();
  const paths = [ 'data-ops-platform/data-ops-platform-security-runtime/pom.xml' ];
  for (const name of SUPPORT_CLASSES) paths.push(starterJava(name), runtimeJava(name));
  for (const name of SUPPORT_TESTS) paths.push(starterTest(name), runtimeTest(name));
  for (const path of paths) {
    const full = resolve(rootDir, path);
    if (existsSync(full)) sources.set(path, readFileSync(full, 'utf8'));
  }
  return sources;
}

export function validateSupportOwners(files) {
  const failures = [];
  for (const name of SUPPORT_CLASSES) {
    if (files.has(starterJava(name))) failures.push('Duplicate Starter runtime owner: ' + name);
    const source = files.get(runtimeJava(name));
    if (!source) {
      failures.push('Missing Platform Runtime source: ' + name);
      continue;
    }
    const javaPackage = 'io.yak.framework.security.' +
      name.substring(0, name.lastIndexOf('/')).replaceAll('/', '.');
    if (!source.includes('package ' + javaPackage + ';')) failures.push('Runtime FQCN drift: ' + name);
    if (/import\s+(?:io\.yak\.framework\.security\.(?:dao|service\.impl|autoconfigure|controller)|io\.yak\.ops\.(?:business|boot|common))\b/.test(source)) {
      failures.push('Runtime must not import Starter implementation or product code: ' + name);
    }
  }
  for (const name of SUPPORT_TESTS) {
    if (files.has(starterTest(name))) failures.push('Old Starter regression test remains: ' + name);
    if (!files.has(runtimeTest(name))) failures.push('Runtime regression test missing: ' + name);
  }
  const props = files.get(runtimeJava('config/YakSecurityProperties.java')) || '';
  if (!props.includes('PREFIX = "yak.security"') ||
      !props.includes('validateDatabaseConfiguration()') ||
      !props.includes('AuthenticationStorage')) failures.push('Security property ABI drift');
  const http = files.get(runtimeJava('util/HttpRequestUtil.java')) || '';
  if (!http.includes('X-YAK-SECURITY-PROJECT-ID') ||
      !http.includes('YakSecurityContext.getCurrentUserId()')) failures.push('Project request identity contract drift');
  const network = files.get(runtimeJava('util/NetworkUtil.java')) || '';
  if (!network.includes('X-Forwarded-For') ||
      !network.includes('getRealIpAddressOrDefault(')) failures.push('Audit IP source contract drift');
  const audit = files.get(runtimeJava('util/SensitiveDataSanitizer.java')) || '';
  if (!audit.includes('BEARER') || !audit.includes('[REDACTED]')) failures.push('Audit secret redaction drift');
  const pom = files.get('data-ops-platform/data-ops-platform-security-runtime/pom.xml') || '';
  for (const artifact of ['data-ops-platform-security-contract', 'lombok',
                          'spring-boot', 'spring-web', 'jakarta.servlet-api']) {
    if (!pom.includes('<artifactId>' + artifact + '</artifactId>')) {
      failures.push('Runtime dependency missing: ' + artifact);
    }
  }
  if (pom.includes('<artifactId>data-security-spring-boot-starter</artifactId>') ||
      /<artifactId>data-ops-(?:common|boot|business)[^<]*<\/artifactId>/.test(pom)) {
    failures.push('Runtime has forbidden reverse Maven dependency');
  }
  return failures;
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const failures = validateSupportOwners(readSupportRepository());
  if (failures.length) {
    console.error(failures.join('\n'));
    process.exitCode = 1;
  } else {
    console.log('A8.2o-p four Security runtime support classes and tests have one owner');
  }
}
