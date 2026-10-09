import test from 'node:test';
import assert from 'node:assert/strict';
import { MOVED, readRepository, validateAuthzOwners }
  from './check-security-authn-context-owner.mjs';
const OLD='data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const NEW='data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/';
test('A8.2k-m six authn/authz/context contracts are solely owned by Platform',()=>{
  assert.equal(MOVED.length,6);
  assert.deepEqual(validateAuthzOwners(readRepository()),[]);
});
test('A8.2k-m refuses duplicate old AuthorizationSnapshot',()=>{
 const files=readRepository();
 files.set(OLD+'context/AuthorizationSnapshot.java',files.get(NEW+'context/AuthorizationSnapshot.java'));
 assert.match(validateAuthzOwners(files).join('\n'),/Framework duplicate owner/);
});
test('A8.2k-m refuses missing AuthenticationManager contract',()=>{
 const files=readRepository();files.delete(NEW+'authentication/AuthenticationManager.java');
 assert.match(validateAuthzOwners(files).join('\n'),/missing Platform owner/);
});
test('A8.2k-m refuses ROOT bypass regression',()=>{
 const files=readRepository(),p=NEW+'context/AuthorizationSnapshot.java';
 files.set(p,files.get(p).replaceAll('SecurityPermissionCode.ROOT','"wrong:root"'));
 assert.match(validateAuthzOwners(files).join('\n'),/ROOT\/Project authorization/);
});
test('A8.2k-m refuses identity ThreadLocal cleanup regression',()=>{
 const files=readRepository(),p=NEW+'context/YakSecurityContext.java';
 files.set(p,files.get(p).replace('HOLDER.remove()','HOLDER.set(null)'));
 assert.match(validateAuthzOwners(files).join('\n'),/ThreadLocal security context/);
});
test('A8.2k-m refuses import from old Sa-Token framework runtime',()=>{
 const files=readRepository(),p=NEW+'authentication/AuthenticationManager.java';
 files.set(p,files.get(p)+'\nimport cn.dev33.satoken.stp.StpLogic;\n');
 assert.match(validateAuthzOwners(files).join('\n'),/must not depend on Framework\/runtime/);
});
test('A8.2k-m refuses removing cache invalidation port',()=>{
 const files=readRepository(),p=NEW+'service/PermissionCache.java';
 files.set(p,files.get(p).replace('void invalidateRole(Long roleId)','void invalidateRoleByName(String roleId)'));
 assert.match(validateAuthzOwners(files).join('\n'),/cache snapshot\/load/);
});
test('A8.2k-m refuses altering original Sa-Token user logout',()=>{
 const files=readRepository(),p=OLD+'authentication/SaTokenAuthenticationManager.java';
 files.set(p,files.get(p).replace('stpLogic.logout(userId)','stpLogic.logout()'));
 assert.match(validateAuthzOwners(files).join('\n'),/Sa-Token adapter/);
});
