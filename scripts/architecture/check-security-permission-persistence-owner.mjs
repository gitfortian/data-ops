#!/usr/bin/env node
/**
 * A8.2e: Permission POJO and DAO interface are owned by the lower Security
 * Platform contract, while MyBatis implementation and Flyway remain in Starter.
 * Static architecture check; real MySQL/PG historical upgrades are separate.
 */
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
export const FILES = Object.freeze({
  oldDao: 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/dao/PermissionDao.java',
  oldModel: 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/common/entity/Permission.java',
  newDao: 'data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/dao/PermissionDao.java',
  newModel: 'data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/common/entity/Permission.java',
  implementation: 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/dao/impl/PermissionDaoImpl.java',
  service: 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/permission/PermissionRegistrationService.java',
  platformPom: 'data-ops-platform/data-ops-platform-security-contract/pom.xml',
  securityPom: 'data-ops-framework/data-security/pom.xml',
});

export function checkPersistencePortOwnership(files) {
  const failures = [];
  if (files.has(FILES.oldDao) || files.has(FILES.oldModel)) {
    failures.push('old Starter cannot own duplicate Permission/PermissionDao class source');
  }
  const dao = files.get(FILES.newDao) || '';
  const model = files.get(FILES.newModel) || '';
  if (!/package io\.yak\.framework\.security\.dao;/.test(dao)
      || !/public interface PermissionDao/.test(dao)
      || !/List<Permission> selectAllAndAscOrderByLevel\(\)/.test(dao)
      || !/void synchronizeDeclared\(List<Permission> permissions\)/.test(dao)) {
    failures.push('Platform PermissionDao legacy FQCN/method ABI changed');
  }
  if (!/package io\.yak\.framework\.security\.common\.entity;/.test(model)
      || !/@Data/.test(model)
      || !/public class Permission\s*\{/.test(model)
      || !/private transient String parentCode;/.test(model)
      || !/private Boolean declared;/.test(model)
      || !/private Boolean active;/.test(model)
      || !/private String menuCode;/.test(model)) {
    failures.push('Platform Permission bean legacy FQCN/property/transient ABI changed');
  }
  const impl = files.get(FILES.implementation) || '';
  if (!/class PermissionDaoImpl implements PermissionDao/.test(impl)
      || !/permissionMapper\.selectList/.test(impl)
      || !/permissionMapper\.insert\(row\)/.test(impl)
      || !/permissionMapper\.updateById\(row\)/.test(impl)
      || !/Boolean\.TRUE\.equals\(item\.getDeclared\(\)\)/.test(impl)
      || !/Boolean\.TRUE\.equals\(item\.getActive\(\)\)/.test(impl)
      || !/row\.setParentId\(parent\.getId\(\)\)/.test(impl)
      || !/if \(StringUtils\.hasText\(item\.getMenuCode\(\)\)\)/.test(impl)) {
    failures.push('Starter must retain historic MyBatis CRUD/reconciliation/mapping semantics');
  }
  const service = files.get(FILES.service) || '';
  if (!/yakSecurityTransactionManager/.test(service)
      || !/permissionDao\.synchronizeDeclared\(desired\)/.test(service)) {
    failures.push('Permission registration must retain dedicated transaction and DAO call');
  }
  const pom = files.get(FILES.platformPom) || '';
  if (!/<artifactId>lombok<\/artifactId>/.test(pom)) {
    failures.push('moved Permission model needs Lombok-generated public bean contract');
  }
  if (/<artifactId>(?:data-security-spring-boot-starter|data-ops-business-[^<]+|data-ops-boot|data-ops-common)<\/artifactId>/.test(pom)) {
    failures.push('Platform must not depend on Framework Security or business modules');
  }
  if (!/<artifactId>data-ops-platform-security-contract<\/artifactId>/.test(
      files.get(FILES.securityPom) || '')) {
    failures.push('Framework Security must consume the single Platform port owner');
  }
  return failures;
}

export function readRepository(root = ROOT) {
  const listed = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], {
    cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024,
  }).split('\0');
  const required = new Set(Object.values(FILES));
  const out = new Map();
  for (const path of listed) {
    if (!required.has(path)) continue;
    const absolute = resolve(root, path);
    if (existsSync(absolute)) out.set(path, readFileSync(absolute, 'utf8'));
  }
  return out;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const violations = checkPersistencePortOwnership(readRepository());
    if (violations.length) {
      console.error(violations.join('\n'));
      process.exitCode = 1;
    } else {
      console.log('A8.2e Security Permission DAO/model single-owner guard passed.');
    }
  } catch (error) {
    console.error('A8.2e persistence port guard failed closed: ' + error.message);
    process.exitCode = 1;
  }
}
