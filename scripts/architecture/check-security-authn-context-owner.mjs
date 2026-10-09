#!/usr/bin/env node
/** Single binary owner for Security authn/authz/runtime identity contracts. */
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const OLD='data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const NEW='data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/';
export const MOVED=[
 'authentication/AuthenticationManager.java','context/AuthorizationSnapshot.java',
 'service/PermissionCache.java','context/CurrentUser.java',
 'context/DefaultCurrentUser.java','context/YakSecurityContext.java'
];
export function validateAuthzOwners(files) {
 const errors=[];
 for(const path of MOVED) {
   if(files.has(OLD+path)) errors.push('Framework duplicate owner: '+path);
   const src=files.get(NEW+path);
   if(!src) { errors.push('missing Platform owner: '+path); continue; }
   const pkg='io.yak.framework.security.'+path.substring(0,path.lastIndexOf('/')).replaceAll('/','.');
   if(!src.includes('package '+pkg+';')) errors.push('public legacy FQCN changed: '+path);
   if(/io\.yak\.framework\.security\.(?:dao|common\.po|service\.impl|autoconfigure|web)\b/.test(src)
      || /cn\.dev33\.satoken|org\.springframework\.transaction|org\.springframework\.web/.test(src)) {
     errors.push('Platform contract must not depend on Framework/runtime: '+path);
   }
 }
 const snap=files.get(NEW+'context/AuthorizationSnapshot.java')||'';
 if(!snap.includes('SecurityPermissionCode.ROOT') ||
    !snap.includes('permissionCodes.contains(SecurityPermissionCode.ROOT)') ||
    !snap.includes('projectIds.contains(projectId)') ||
    !snap.includes('Collections.unmodifiableSet') ||
    !snap.includes('Collections.unmodifiableList')) {
   errors.push('ROOT/Project authorization and immutable snapshot semantics must remain');
 }
 const ctx=files.get(NEW+'context/YakSecurityContext.java')||'';
 if(!ctx.includes('ThreadLocal<CurrentUser>') ||
    !ctx.includes('HOLDER.remove()') ||
    !ctx.includes('private static final CurrentUser ANONYMOUS') ||
    !ctx.includes('static void setCurrentUser(')) {
   errors.push('anonymous ThreadLocal security context entry/clear ABI must remain');
 }
 const auth=files.get(NEW+'authentication/AuthenticationManager.java')||'';
 if(!auth.includes('default void logoutUser(Long userId)') ||
    !auth.includes('default String getLoginUsername()') ||
    !auth.includes('default void login(Long userId, String userName)')) {
   errors.push('AuthenticationManager default login/logout compatibility must remain');
 }
 const cache=files.get(NEW+'service/PermissionCache.java')||'';
 if(!cache.includes('default AuthorizationSnapshot getAuthorizationSnapshot(') ||
    !cache.includes('void invalidateRole(Long roleId)') ||
    !cache.includes('void invalidateUser(Long userId)')) {
   errors.push('Permission cache snapshot/load and invalidation ABI must remain');
 }
 const impl=files.get(OLD+'authentication/SaTokenAuthenticationManager.java')||'';
 if(!impl.includes('implements AuthenticationManager') ||
    !impl.includes('yak-security:username') ||
    !impl.includes('stpLogic.logout(userId)')) {
   errors.push('Sa-Token adapter must retain original login/session implementation');
 }
 const filter=files.get(OLD+'context/YakSecurityContextFilter.java')||'';
 if(!filter.includes('YakSecurityContext.setCurrentUser(') ||
    !filter.includes('YakSecurityContext.clear()')) {
   errors.push('existing web filter must set/clear the same Platform ThreadLocal');
 }
 return errors;
}
export function readRepository(root=ROOT) {
 const paths=new Set([...MOVED.flatMap(p=>[OLD+p,NEW+p]),
    OLD+'authentication/SaTokenAuthenticationManager.java',
    OLD+'context/YakSecurityContextFilter.java']);
 const listed=execFileSync('git',['ls-files','--cached','--others','--exclude-standard','-z'],
   {cwd:root,encoding:'utf8',maxBuffer:16*1024*1024}).split('\0');
 const files=new Map();
 for(const path of listed) {
   if(!paths.has(path))continue;
   const full=resolve(root,path);
   if(existsSync(full))files.set(path,readFileSync(full,'utf8'));
 }
 return files;
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 try {
   const errors=validateAuthzOwners(readRepository());
   if(errors.length) {console.error(errors.join('\n'));process.exitCode=1;}
   else console.log('A8.2k-m security authn/authz/context ownership passed.');
 }catch(e){console.error('A8.2k-m failed closed: '+e.message);process.exitCode=1;}
}
