#!/usr/bin/env node
/**
 * A8.2g: independent Project/RBAC membership ports belong to Security Platform;
 * existing legacy DAOs still declare all methods and provide PO/DTO reads.
 */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const PLATFORM = 'data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/ops/platform/security/port/';
const FRAMEWORK = 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
export const PATHS = {
  project: PLATFORM + 'UserProjectMembershipPort.java',
  role: PLATFORM + 'UserRoleAssignmentPort.java',
  projectDao: FRAMEWORK + 'dao/UserProjectDao.java',
  roleDao: FRAMEWORK + 'dao/UserRoleDao.java',
  projectService: FRAMEWORK + 'service/impl/UserProjectServiceImpl.java',
  roleService: FRAMEWORK + 'service/impl/UserRoleServiceImpl.java',
  projectImpl: FRAMEWORK + 'dao/impl/UserProjectDaoImpl.java',
  roleImpl: FRAMEWORK + 'dao/impl/UserRoleDaoImpl.java',
  platformPom: 'data-ops-platform/data-ops-platform-security-contract/pom.xml',
};

const projectMethods = [
  'selectUserIdListByProjectId','selectProjectIdListByUserIdList','insertBatch',
  'deleteUserProject','deleteByProjectId','deleteByUserId',
  'deleteByProjectIdAndUserType','selectByProjectIds','selectMembershipsByCriteria',
];
const roleMethods = [
  'selectUserIdListByRoleId','selectRoleIdListByUserId','insertBatch',
  'deleteByUserIdOrRoleId','selectCountByRoleId',
  'selectAssignmentsByRoleIds','selectAssignmentsByUserIds',
];

export function validateMembershipPorts(files) {
  const errors = [];
  for (const [kind, type, methods] of [
    ['project','UserProjectMembershipPort',projectMethods],
    ['role','UserRoleAssignmentPort',roleMethods],
  ]) {
    const src = files[kind] || '';
    if (!src.includes('package io.yak.ops.platform.security.port;') ||
        !src.includes('public interface ' + type + ' {')) {
      errors.push('Platform must own ' + type);
    }
    for (const method of methods) {
      if (!new RegExp('\\b' + method + '\\(').test(src)) {
        errors.push(type + ' missing method ' + method);
      }
    }
    if (/io\.yak\.framework\.security\.(?:common\.dto|common\.po|dao|dao\.impl|service)\b/.test(src) ||
        /org\.springframework\.transaction|org\.springframework\.stereotype|com\.baomidou/.test(src)) {
      errors.push(type + ' may not use legacy DTO/PO, DAO, transactions or MyBatis');
    }
  }
  for (const [name, type] of [['projectDao','UserProjectMembershipPort'],['roleDao','UserRoleAssignmentPort']]) {
    const src = files[name] || '';
    const expected = name === 'projectDao' ? 'UserProjectDao' : 'UserRoleDao';
    if (!src.includes('import io.yak.ops.platform.security.port.' + type + ';') ||
        !new RegExp('public interface ' + expected + ' extends ' + type).test(src)) {
      errors.push('Legacy ' + expected + ' must preserve its FQCN and extend new port');
    }
    if (!src.includes('common.po.') || (name==='projectDao' && !src.includes('common.dto.'))) {
      errors.push('Legacy ' + expected + ' retains incompatible PO/DTO bridge until next migration');
    }
  }
  for (const [name,type,field,legacyField] of [
    ['projectService','UserProjectMembershipPort','membershipPort','userProjectDao'],
    ['roleService','UserRoleAssignmentPort','assignmentPort','userRoleDao'],
  ]) {
    const src = files[name] || '';
    if (!src.includes('private final ' + type + ' ' + field + ';') ||
        !src.includes('this.' + field + ' = ' + legacyField + ';') ||
        !src.includes('this.' + legacyField + ' = ' + legacyField + ';') ||
        !new RegExp('\\b' + field + '\\s*\\.\\s*').test(src)) {
      errors.push(name + ' must route pure operations through Platform using the identical legacy DAO instance');
    }
    if (!src.includes('yakSecurityTransactionManager') ||
        !/permissionCache(?:\.|::)invalidate/.test(src)) {
      errors.push(name + ' must retain Security transactions and cache invalidation');
    }
  }
  const pService = files.projectService || '';
  if (!pService.includes('membershipPort.selectMembershipsByCriteria(') ||
      !pService.includes('new UserProjectCriteria(')) {
    errors.push('Project read must use Platform criteria, not legacy DTO DAO');
  }
  const pImpl = files.projectImpl || '';
  if (!pImpl.includes('UserProjectDTO dto = new UserProjectDTO()') ||
      !pImpl.includes('return select(dto);')) {
    errors.push('Project adapter must retain legacy DTO query translation');
  }
  const rService = files.roleService || '';
  if (!rService.includes('assignmentPort.selectAssignmentsByRoleIds(') ||
      !rService.includes('assignmentPort.selectAssignmentsByUserIds(')) {
    errors.push('Role service must consume Platform PO-free read projections');
  }
  const rImpl = files.roleImpl || '';
  if (!rImpl.includes('CopyBeanUtil.copyList(selectByRoleIds(') ||
      !rImpl.includes('CopyBeanUtil.copyList(getRoleIdListByUserIds(')) {
    errors.push('Role legacy DAO must retain PO-to-entity translation');
  }
  for (const [name,type] of [['projectImpl','UserProjectDao'],['roleImpl','UserRoleDao']]) {
    const src = files[name] || '';
    if (!src.includes('implements ' + type) ||
        !src.includes('org.springframework.stereotype.Repository')) {
      errors.push(name + ' must remain original Spring MyBatis bean, not a second Platform implementation');
    }
  }
  if (/<artifactId>(?:data-security-spring-boot-starter|data-ops-boot|data-ops-common|data-ops-business-[^<]+)<\/artifactId>/.test(files.platformPom || '')) {
    errors.push('Platform contract must not acquire reverse dependencies');
  }
  return errors;
}

export function readRepository(root = ROOT) {
  return Object.fromEntries(Object.entries(PATHS).map(([name,path]) =>
    [name, readFileSync(resolve(root,path),'utf8')]));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const errors = validateMembershipPorts(readRepository());
    if (errors.length) {
      console.error(errors.join('\n'));
      process.exitCode = 1;
    } else {
      console.log('A8.2g Security Project/Role Platform ports and legacy bridges passed.');
    }
  } catch (error) {
    console.error('A8.2g failed closed: ' + error.message);
    process.exitCode = 1;
  }
}
