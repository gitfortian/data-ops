#!/usr/bin/env node
/**
 * A8.2k-m: pure authentication, authorization, request identity, User/Dept,
 * and menu-selection contracts have a single Security Platform source owner.
 * Never move Sa-Token, HTTP filters, cache implementations or Spring Beans here.
 */
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const OLD = 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const NEW = 'data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/';
export const TYPES = [
  'authentication/AuthenticationManager.java',
  'context/AuthorizationSnapshot.java',
  'context/CurrentUser.java',
  'context/DefaultCurrentUser.java',
  'context/YakSecurityContext.java',
  'service/PermissionCache.java',
  'service/impl/MenuSelectionCodec.java',
  'common/entity/user/User.java',
  'common/entity/user/UserBrief.java',
  'common/entity/dept/Dept.java',
  'common/entity/dept/DeptBrief.java',
];
const LEGACY = {
  tokenAdapter: OLD + 'authentication/SaTokenAuthenticationManager.java',
  servletFilter: OLD + 'context/YakSecurityContextFilter.java',
  trustedJob: OLD + 'context/TrustedUserScope.java',
  cacheAdapter: OLD + 'service/impl/CaffeinePermissionCache.java',
  platformPom: 'data-ops-platform/data-ops-platform-security-contract/pom.xml',
  starterPom: 'data-ops-framework/data-security/pom.xml',
};
const FORBIDDEN = /(?:import|extends|implements)\s+(?:io\.yak\.framework\.security\.(?:dao|common\.po|autoconfigure|controller)|cn\.dev33|jakarta\.servlet|org\.springframework|com\.baomidou|org\.mybatis)/;

export function validateIdentityOwnership(files) {
  const errors = [];
  for (const name of TYPES) {
    if (files.has(OLD + name)) errors.push('duplicate legacy Security class: ' + name);
    const source = files.get(NEW + name);
    if (!source) {
      errors.push('missing Platform Security class: ' + name);
      continue;
    }
    const pkg = name.slice(0, name.lastIndexOf('/')).replaceAll('/', '.');
    const typeName = name.split('/').at(-1).replace('.java','');
    if (!source.includes('package io.yak.framework.security.' + pkg + ';')
        || !source.includes(typeName)) {
      errors.push('original Security type/FQCN changed: ' + name);
    }
    if (FORBIDDEN.test(source)
        || source.includes('import io.yak.framework.security.common.constant.SecurityPermissionCode;')) {
      errors.push('Platform pure identity/model cannot import HTTP/DB/Sa-Token or legacy Starter facade: ' + name);
    }
  }
  const snap = files.get(NEW + 'context/AuthorizationSnapshot.java') || '';
  if (!snap.includes('import io.yak.ops.platform.security.contract.SecurityPermissionCode;') ||
      (snap.match(/SecurityPermissionCode\.ROOT/g) || []).length !== 2 ||
      !snap.includes('permissionCodes.contains(permissionCode)') ||
      !snap.includes('projectIds.contains(projectId)') ||
      !snap.includes('Collections.unmodifiableList') ||
      !snap.includes('Collections.unmodifiableSet')) {
    errors.push('authorization snapshot immutability/root/Project access behavior changed');
  }
  const context = files.get(NEW + 'context/YakSecurityContext.java') || '';
  if (!context.includes('ThreadLocal<CurrentUser>') ||
      !context.includes('HOLDER.remove()') ||
      !context.includes('static final class ImmutableCurrentUser')) {
    errors.push('request identity ThreadLocal cleanup and immutable scope contract changed');
  }
  const login = files.get(NEW + 'authentication/AuthenticationManager.java') || '';
  if (!login.includes('default void logoutUser(Long userId)') ||
      !login.includes('default String getLoginUsername()') ||
      !login.includes('default void login(Long userId, String userName)')) {
    errors.push('legacy custom authentication implementation default ABI changed');
  }
  const cache = files.get(NEW + 'service/PermissionCache.java') || '';
  if (!cache.includes('default AuthorizationSnapshot getAuthorizationSnapshot') ||
      !cache.includes('void invalidateUser(Long userId)') ||
      !cache.includes('void invalidateRole(Long roleId)')) {
    errors.push('legacy custom cache ABI and invalidation contract changed');
  }
  const codec = files.get(NEW + 'service/impl/MenuSelectionCodec.java') || '';
  if (!codec.includes('MENU_GROUP_NODE_ID = -1L') ||
      !codec.includes('return -(menuId + 1L)') ||
      !codec.includes('decodeMenuId') ||
      !codec.includes('extractMenuIds')) {
    errors.push('role menu permission selection encoding protocol changed');
  }
  const user = files.get(NEW + 'common/entity/user/User.java') || '';
  if (!user.includes('extends BaseEntity') ||
      !user.includes('@ToString.Exclude') ||
      !user.includes('private String pw;') ||
      !user.includes('private String salt;')) {
    errors.push('User base entity or sensitive credential masking changed');
  }
  const token = files.get(LEGACY.tokenAdapter) || '';
  const servlet = files.get(LEGACY.servletFilter) || '';
  const trusted = files.get(LEGACY.trustedJob) || '';
  const cacheAdapter = files.get(LEGACY.cacheAdapter) || '';
  if (!token.includes('implements AuthenticationManager') ||
      !servlet.includes('YakSecurityContext.setCurrentUser') ||
      !servlet.includes('YakSecurityContext.clear()') ||
      !trusted.includes('YakSecurityContext.ImmutableCurrentUser') ||
      !cacheAdapter.includes('implements PermissionCache')) {
    errors.push('Sa-Token, HTTP filter, background task and cache adapters must remain in Starter');
  }
  const pom=files.get(LEGACY.platformPom)||'';
  if (/<artifactId>(?:data-security-spring-boot-starter|data-ops-common|data-ops-boot|data-ops-business-[^<]+)<\/artifactId>/.test(pom)) {
    errors.push('Platform must never reverse depend on Framework or Product upper layers');
  }
  if (!(files.get(LEGACY.starterPom)||'').includes('<artifactId>data-ops-platform-security-contract</artifactId>')) {
    errors.push('old Starter must resolve relocated Security contracts from Platform');
  }
  return errors;
}

export function readRepository(root=ROOT) {
  const required = new Set([
    ...TYPES.flatMap(t => [OLD + t, NEW + t]), ...Object.values(LEGACY),
  ]);
  const list=execFileSync('git',['ls-files','--cached','--others','--exclude-standard','-z'],
    {cwd:root,encoding:'utf8',maxBuffer:16*1024*1024}).split('\0');
  const files=new Map();
  for (const p of list) {
    if (!required.has(p)) continue;
    const absolute=resolve(root,p);
    if (existsSync(absolute)) files.set(p,readFileSync(absolute,'utf8'));
  }
  return files;
}
if (process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  try {
    const errors=validateIdentityOwnership(readRepository());
    if (errors.length) {
      console.error(errors.join('\n'));
      process.exitCode=1;
    } else {
      console.log('A8.2k-m 11 Security identity, auth and User/Dept class owners verified.');
    }
  } catch(error) {
    console.error('A8.2k-m identity ownership failed closed: '+error.message);
    process.exitCode=1;
  }
}
