#!/usr/bin/env node
/**
 * A8.2f: exactly one source owner for Project/RBAC membership models and the
 * RolePermission persistence port. The existing Spring/MyBatis implementations
 * must remain in the Starter; the Platform contract cannot reference them.
 */
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const OLD = 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const NEW = 'data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/';
export const MIGRATED = [
  'common/entity/UserProject.java',
  'common/entity/UserRole.java',
  'common/entity/RolePermission.java',
  'dao/RolePermissionDao.java',
];
export const RELATED = {
  rbacImpl: OLD + 'dao/impl/RolePermissionDaoImpl.java',
  userProjectDao: OLD + 'dao/UserProjectDao.java',
  userRoleDao: OLD + 'dao/UserRoleDao.java',
  service: OLD + 'service/impl/RolePermissionServiceImpl.java',
  platformPom: 'data-ops-platform/data-ops-platform-security-contract/pom.xml',
  starterPom: 'data-ops-framework/data-security/pom.xml',
};

export function validateRelations(files) {
  const errors = [];
  for (const path of MIGRATED) {
    if (files.has(OLD + path)) errors.push('duplicate Framework source: ' + path);
    const src = files.get(NEW + path);
    if (!src) {
      errors.push('missing Platform source: ' + path);
    } else {
      const pkg = path.startsWith('dao/') ? 'io.yak.framework.security.dao'
        : 'io.yak.framework.security.common.entity';
      if (!src.includes('package ' + pkg + ';')) errors.push('legacy FQCN moved: ' + path);
      if (/io\.yak\.framework\.security\.(?:common\.po|dao\.impl|dao\.mapper|service|autoconfigure)\b/.test(src)
          || /org\.springframework\.transaction/.test(src)) {
        errors.push('Platform relation port must not depend on persistence implementation: ' + path);
      }
    }
  }
  const project = files.get(NEW + 'common/entity/UserProject.java') || '';
  const userRole = files.get(NEW + 'common/entity/UserRole.java') || '';
  const rolePermission = files.get(NEW + 'common/entity/RolePermission.java') || '';
  const port = files.get(NEW + 'dao/RolePermissionDao.java') || '';
  if (!/@Data/.test(project)
      || !/private Long userId;/.test(project)
      || !/private Integer userType;/.test(project)
      || !/private Long projectId;/.test(project)) {
    errors.push('Project membership identity and Lombok ABI changed');
  }
  if (!/@Data/.test(userRole) || !/@AllArgsConstructor/.test(userRole)
      || !/@NoArgsConstructor/.test(userRole)
      || !/private Long roleId;/.test(userRole)
      || !/private Long userId;/.test(userRole)) {
    errors.push('User-role membership constructor ABI changed');
  }
  if (!/@Data/.test(rolePermission)
      || !/private Long roleId;/.test(rolePermission)
      || !/private Long permissionId;/.test(rolePermission)) {
    errors.push('Role-permission grant identity changed');
  }
  if (!/public interface RolePermissionDao/.test(port)
      || !/void insertBatch\(List<RolePermission>/.test(port)
      || !/void deleteByRoleId\(Long roleId\)/.test(port)
      || !/void deleteByPermissionId\(Long permissionId\)/.test(port)
      || !/List<Long> selectPermissionIdListByRoleId\(Long roleId\)/.test(port)
      || !/List<Long> selectPermissionIdListByRoleIdList\(List<Long>/.test(port)) {
    errors.push('RolePermissionDao public persistence contract changed');
  }
  const impl = files.get(RELATED.rbacImpl) || '';
  if (!/implements RolePermissionDao/.test(impl)
      || !/rolePermissionMapper\.delete/.test(impl)
      || !/rolePermissionMapper\.selectObjs/.test(impl)
      || !/rolePermissionMapper::insert/.test(impl)) {
    errors.push('Starter must retain MyBatis role grants persistence implementation');
  }
  const service = files.get(RELATED.service) || '';
  if (!/yakSecurityTransactionManager/.test(service)
      || !/permissionCache\.invalidateRole\(roleId\)/.test(service)
      || !/permissionCache\.invalidateAll\(\)/.test(service)
      || !/rolePermissionDao\.deleteByRoleId/.test(service)) {
    errors.push('RBAC transaction/cache invalidation or revocation semantics changed');
  }
  if (!files.has(RELATED.userProjectDao) || !files.has(RELATED.userRoleDao)) {
    errors.push('PO/DTO-dependent membership DAOs must remain in Starter until dedicated migration');
  }
  const pom = files.get(RELATED.platformPom) || '';
  if (!/<artifactId>lombok<\/artifactId>/.test(pom)
      || /<artifactId>(?:data-security-spring-boot-starter|data-ops-business-[^<]+|data-ops-boot|data-ops-common)<\/artifactId>/.test(pom)) {
    errors.push('Platform relation owner must retain Lombok, and avoid reverse dependencies');
  }
  if (!/<artifactId>data-ops-platform-security-contract<\/artifactId>/.test(
      files.get(RELATED.starterPom) || '')) {
    errors.push('Starter must consume Platform relation port owner');
  }
  return errors;
}

export function readRepository(root = ROOT) {
  const names = new Set([
    ...MIGRATED.flatMap(p => [OLD + p, NEW + p]),
    ...Object.values(RELATED),
  ]);
  const gitFiles = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], {
    cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024,
  }).split('\0');
  const files = new Map();
  for (const path of gitFiles) {
    if (!names.has(path)) continue;
    const full = resolve(root, path);
    if (existsSync(full)) files.set(path, readFileSync(full, 'utf8'));
  }
  return files;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const errors = validateRelations(readRepository());
    if (errors.length) {
      console.error(errors.join('\n'));
      process.exitCode = 1;
    } else {
      console.log('A8.2f Project/RBAC relation and DAO single ownership verified.');
    }
  } catch (error) {
    console.error('A8.2f relation owner guard failed closed: ' + error.message);
    process.exitCode = 1;
  }
}
