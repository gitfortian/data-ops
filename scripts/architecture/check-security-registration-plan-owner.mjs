#!/usr/bin/env node
/**
 * A8.2d: declaration planning has one lower-layer owner and Security Starter
 * continues to own all transaction and persistence adapters.
 */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
export const PATHS = Object.freeze({
  plan: 'data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/permission/PermissionDeclarationPlan.java',
  service: 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/permission/PermissionRegistrationService.java',
  dao: 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/dao/PermissionDao.java',
  contractPom: 'data-ops-platform/data-ops-platform-security-contract/pom.xml',
  securityPom: 'data-ops-framework/data-security/pom.xml',
});

export function checkPlannerOwnership(files) {
  const errors = [];
  const plan = files.plan || '';
  const service = files.service || '';
  const contractPom = files.contractPom || '';
  const securityPom = files.securityPom || '';
  if (!/package io\.yak\.framework\.security\.permission;/.test(plan)
      || !/public final class PermissionDeclarationPlan/.test(plan)
      || !/public static PermissionDeclarationPlan from\(Collection<PermissionDefinition> definitions\)/.test(plan)) {
    errors.push('Platform must own stable permission declaration planning API');
  }
  if (!/LinkedHashMap/.test(plan) || !/putIfAbsent/.test(plan)
      || !/Conflicting permission declaration: /.test(plan)
      || !/List\.copyOf\(entries\)/.test(plan)) {
    errors.push('Platform must preserve ordering/first-wins/conflict/immutable semantics');
  }
  if (/io\.yak\.framework\.security\.(?:dao|common\.entity|autoconfigure|service)\b/.test(plan)
      || /@(?:Component|Service|Repository|Transactional)\b/.test(plan)
      || /org\.springframework\.transaction/.test(plan)) {
    errors.push('Platform planning must remain persistence and Spring transaction free');
  }
  if (!/PermissionDeclarationPlan\.from\(definitions\)\.entries\(\)/.test(service)) {
    errors.push('Starter must delegate planning to Platform owner');
  }
  if (!/@Transactional\(transactionManager = "yakSecurityTransactionManager"\)/.test(service)
      || !/permissionDao\.synchronizeDeclared\(desired\)/.test(service)
      || !/permission\.setParentCode\(entry\.parentCode\(\)\)/.test(service)
      || !/permission\.setActive\(true\)/.test(service)
      || !/permission\.setDeclared\(true\)/.test(service)) {
    errors.push('Starter must preserve transaction, DAO, Project parent and declared/active behavior');
  }
  if (!/<artifactId>data-ops-platform-security-contract<\/artifactId>/.test(securityPom)) {
    errors.push('Security Starter must consume Platform contract');
  }
  if (/<artifactId>(?:data-security-spring-boot-starter|data-ops-business-[^<]+|data-ops-boot|data-ops-common)<\/artifactId>/.test(contractPom)) {
    errors.push('Platform must not reverse-depend on Framework or product business');
  }
  if (!/void synchronizeDeclared\(List<Permission>/.test(files.dao || '')) {
    errors.push('Existing PermissionDao synchronization contract missing');
  }
  return errors;
}

export function readRepository(root = ROOT) {
  return Object.fromEntries(Object.entries(PATHS).map(([name, path]) =>
    [name, readFileSync(resolve(root, path), 'utf8')]));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const errors = checkPlannerOwnership(readRepository());
    if (errors.length) {
      console.error(errors.join('\n'));
      process.exitCode = 1;
    } else {
      console.log('A8.2d permission declaration planning owner and persistence boundary passed.');
    }
  } catch (error) {
    console.error('A8.2d failed closed: ' + error.message);
    process.exitCode = 1;
  }
}
