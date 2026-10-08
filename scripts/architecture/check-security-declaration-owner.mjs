#!/usr/bin/env node
/**
 * A8.2c Security permission declaration owner migration.
 * Keep the legacy FQCN and runtime reflection shape, but ensure exactly
 * one source/class owner and no lower-layer reverse dependencies.
 */
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
export const NAMES = Object.freeze([
  'YakPermission', 'PermissionDefinition', 'PermissionDefinitionProvider',
]);
export const OLD_DIR =
  'data-ops-framework/data-security/src/main/java/io/yak/framework/security/permission/';
export const NEW_DIR =
  'data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/permission/';
const PACKAGE = 'package io.yak.framework.security.permission;';
const CONTRACT_POM = 'data-ops-platform/data-ops-platform-security-contract/pom.xml';
const SECURITY_POM = 'data-ops-framework/data-security/pom.xml';
const STARTER = OLD_DIR + 'PermissionRegistrationInitializer.java';

export function validateDeclarationOwnership(files) {
  const issues = [];
  for (const name of NAMES) {
    const old = OLD_DIR + name + '.java';
    const migrated = NEW_DIR + name + '.java';
    if (files.has(old)) issues.push(old + ': duplicate old owner must be removed');
    if (!files.has(migrated)) {
      issues.push(migrated + ': missing migrated ABI');
    } else if (!files.get(migrated).includes(PACKAGE)) {
      issues.push(migrated + ': original package/FQCN must be retained');
    }
  }
  const annotation = files.get(NEW_DIR + 'YakPermission.java') || '';
  if (!/@Retention\(RetentionPolicy\.RUNTIME\)/.test(annotation) ||
      !/@Target\(\{ElementType\.TYPE, ElementType\.METHOD\}\)/.test(annotation) ||
      !/public @interface YakPermission\s*\{/.test(annotation)) {
    issues.push('YakPermission annotation runtime retention/targets/type contract changed');
  }
  const definition = files.get(NEW_DIR + 'PermissionDefinition.java') || '';
  if (!/public final class PermissionDefinition\s*\{/.test(definition) ||
      !/public static final class Item\s*\{/.test(definition) ||
      !/static PermissionDefinition fromItems\(/.test(definition)) {
    issues.push('PermissionDefinition or nested Item compatibility contract changed');
  }
  const provider = files.get(NEW_DIR + 'PermissionDefinitionProvider.java') || '';
  if (!/@FunctionalInterface/.test(provider) ||
      !/List<PermissionDefinition> getPermissionDefinitions\(\)/.test(provider)) {
    issues.push('PermissionDefinitionProvider compatibility contract changed');
  }
  if (!files.has(STARTER) || !files.get(STARTER).includes('YakPermission.class') ||
      !files.get(STARTER).includes('PermissionDefinitionProvider.class')) {
    issues.push('existing permission registration still must discover legacy FQCN contracts');
  }
  const pom = files.get(CONTRACT_POM) || '';
  if (!/<artifactId>spring-core<\/artifactId>/.test(pom)) {
    issues.push('migrated PermissionDefinition must retain original Spring validation utilities');
  }
  if (/<artifactId>(?:data-security-spring-boot-starter|data-ops-common|data-ops-boot|data-ops-business-[^<]+)<\/artifactId>/.test(pom)) {
    issues.push('Platform contract must never depend back on Security starter/Product business');
  }
  if (!/<artifactId>data-ops-platform-security-contract<\/artifactId>/.test(files.get(SECURITY_POM) || '')) {
    issues.push('Security Starter must consume the same Platform contract owner');
  }
  return issues.sort();
}

export function readRepository(root = ROOT) {
  const raw = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], {
    cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024,
  });
  const files = new Map();
  const candidates = new Set([
    CONTRACT_POM, SECURITY_POM, STARTER,
    ...NAMES.flatMap(name => [OLD_DIR + name + '.java', NEW_DIR + name + '.java']),
  ]);
  for (const path of raw.split('\0')) {
    if (!candidates.has(path)) continue;
    const filename = resolve(root, path);
    if (existsSync(filename)) files.set(path, readFileSync(filename, 'utf8'));
  }
  return files;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const problems = validateDeclarationOwnership(readRepository());
    if (problems.length) {
      console.error(problems.join('\n'));
      process.exitCode = 1;
    } else {
      console.log('A8.2c migrated permission declarations: single ABI owner verified.');
    }
  } catch (error) {
    console.error('A8.2c permission declaration guard failed closed: ' + error.message);
    process.exitCode = 1;
  }
}
