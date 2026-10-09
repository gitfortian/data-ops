import test from 'node:test';
import assert from 'node:assert/strict';
import { MOVED, readRepository, validateModelOwners }
  from './check-security-project-role-model-owner.mjs';

const OLD='data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const NEW='data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/';

test('A8.2h all six Project/Role model FQCNs are uniquely owned by Platform',()=>{
  assert.equal(MOVED.length,6);
  assert.deepEqual(validateModelOwners(readRepository()),[]);
});
test('A8.2h rejects duplicate legacy BaseEntity class',()=>{
  const files=readRepository();
  files.set(OLD+'common/entity/BaseEntity.java',files.get(NEW+'common/entity/BaseEntity.java'));
  assert.match(validateModelOwners(files).join('\n'),/duplicate Framework model/);
});
test('A8.2h rejects missing RoleBrief product owner',()=>{
  const files=readRepository();
  files.delete(NEW+'common/entity/role/RoleBrief.java');
  assert.match(validateModelOwners(files).join('\n'),/missing Platform model/);
});
test('A8.2h refuses to rename the existing Project Java package',()=>{
  const files=readRepository(),p=NEW+'common/entity/project/Project.java';
  files.set(p,files.get(p).replace('package io.yak.framework.security.common.entity.project;',
    'package io.yak.ops.platform.security.project;'));
  assert.match(validateModelOwners(files).join('\n'),/historic Java FQCN/);
});
test('A8.2h preserves owner/member Project userType query dimension',()=>{
  const files=readRepository(),p=NEW+'common/dto/user/UserProjectDTO.java';
  files.set(p,files.get(p).replace('private Integer userType;','private Integer roleType;'));
  assert.match(validateModelOwners(files).join('\n'),/userType/);
});
test('A8.2h blocks Platform entity from depending on Framework DAO',()=>{
  const files=readRepository(),p=NEW+'common/entity/role/Role.java';
  files.set(p,files.get(p)+'\nimport io.yak.framework.security.dao.ProjectDao;\n');
  assert.match(validateModelOwners(files).join('\n'),/must not reference database runtime/);
});
test('A8.2h blocks reverse Maven dependency on old Security',()=>{
  const files=readRepository(),p='data-ops-platform/data-ops-platform-security-contract/pom.xml';
  files.set(p,files.get(p).replace('<dependencies>',
    '<dependencies><dependency><artifactId>data-security-spring-boot-starter</artifactId></dependency>'));
  assert.match(validateModelOwners(files).join('\n'),/unidirectional/);
});
