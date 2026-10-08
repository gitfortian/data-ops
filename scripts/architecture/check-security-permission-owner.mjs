#!/usr/bin/env node
/**
 * A8.2b: Guard the first actual Security Platform contract ownership transfer.
 * Old FQCN remains a constant-compatible facade, never a second value owner.
 */
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const OLD = 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/common/constant/SecurityPermissionCode.java';
const NEW = 'data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/ops/platform/security/contract/SecurityPermissionCode.java';
const PREFIX = 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/controller/v1/';
const CONTROLLERS = ['DeptController', 'PermissionController', 'ProjectController', 'RoleController', 'UserController'];

function fields(source) {
  const result = new Map();
  let nested = '';
  for (const line of source.split(/\r?\n/)) {
    const inner = line.match(/^\s*public static final class (\w+)\s*\{/);
    if (inner) nested = inner[1];
    const item = line.match(/^\s*public static final String\s+(\w+)\s*=\s*(.+);\s*$/);
    if (item) result.set((nested ? nested + '.' : '') + item[1], item[2].trim());
  }
  return result;
}

export function validatePermissionOwner(files) {
  const errors = [];
  const root = files['pom.xml'] || '';
  const frameworkPom = files['data-ops-framework/data-security/pom.xml'] || '';
  const platformPom = files['data-ops-platform/data-ops-platform-security-contract/pom.xml'] || '';
  const legacy = files[OLD] || '';
  const canonical = files[NEW] || '';

  if (!root.includes('<module>data-ops-platform/data-ops-platform-security-contract</module>')) {
    errors.push('root reactor missing Security Platform contract');
  }
  if (!/<artifactId>data-ops-platform-security-contract<\/artifactId>/.test(frameworkPom) ||
      !/<groupId>io\.yak\.ops<\/groupId>/.test(frameworkPom)) {
    errors.push('old Security starter must consume Platform Security contract');
  }
  if (!platformPom.includes('<artifactId>data-ops-platform-security-contract</artifactId>') ||
      /<artifactId>(?:data-ops-common|data-ops-boot|data-ops-business-[^<]+|data-security-spring-boot-starter)<\/artifactId>/.test(platformPom)) {
    errors.push('Platform contract must remain independent of Common/Business/Boot/Framework Security');
  }
  if (!canonical.includes('package io.yak.ops.platform.security.contract;') ||
      !legacy.includes('package io.yak.framework.security.common.constant;')) {
    errors.push('canonical or legacy package changed');
  }
  const expected = fields(canonical), delegates = fields(legacy);
  if (expected.size !== 28 || delegates.size !== expected.size) {
    errors.push('expected exactly 28 canonical and 28 legacy permission constants');
  }
  for (const [name, value] of expected) {
    if (!/^"[^"]*"$/.test(value)) errors.push('canonical value must remain a literal: ' + name);
    const actual = delegates.get(name);
    const mustBe = 'io.yak.ops.platform.security.contract.SecurityPermissionCode.' + name;
    if (actual !== mustBe) errors.push('legacy field must forward to canonical owner: ' + name);
  }
  for (const name of delegates.keys()) {
    if (!expected.has(name)) errors.push('unknown legacy field: ' + name);
  }
  for (const name of CONTROLLERS) {
    const controller = files[PREFIX + name + '.java'] || '';
    if (!controller.includes('import io.yak.ops.platform.security.contract.SecurityPermissionCode;') ||
        controller.includes('import io.yak.framework.security.common.constant.SecurityPermissionCode;')) {
      errors.push(name + ' must import canonical permission contract');
    }
  }
  return errors;
}

export function readRepository(root = ROOT) {
  const paths = ['pom.xml', 'data-ops-framework/data-security/pom.xml',
      'data-ops-platform/data-ops-platform-security-contract/pom.xml', OLD, NEW,
      ...CONTROLLERS.map(name => PREFIX + name + '.java')];
  return Object.fromEntries(paths.map(path =>
    [path, readFileSync(resolve(root, path), 'utf8')]));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const errors = validatePermissionOwner(readRepository());
    if (errors.length) {
      console.error(errors.join('\n'));
      process.exitCode = 1;
    } else {
      console.log('A8.2b Security Platform permission owner guard passed.');
    }
  } catch (error) {
    console.error('A8.2b Security permission owner guard failed closed: ' + error.message);
    process.exitCode = 1;
  }
}
