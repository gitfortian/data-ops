import test from 'node:test';
import assert from 'node:assert/strict';
import { CONTRACT_TYPES, readRepository, validateApiContracts }
 from './check-security-api-contract-owner.mjs';
const OLD='data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const NEW='data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/';
const errors=(files)=>validateApiContracts(files).join('\n');
test('all 75 Security API owners are in Platform with no Starter duplicate',()=>{
 assert.equal(CONTRACT_TYPES.length,75);
 assert.deepEqual(validateApiContracts(readRepository()),[]);
});
test('rejects duplicate request DTO',()=>{
 const f=readRepository(),p='common/dto/account/AccountLoginDTO.java';
 f.set(OLD+p,f.get(NEW+p));assert.match(errors(f),/duplicate Starter/);
});
test('rejects duplicate role response VO',()=>{
 const f=readRepository(),p='common/vo/role/RoleVO.java';
 f.set(OLD+p,f.get(NEW+p));assert.match(errors(f),/duplicate Starter/);
});
test('rejects missing DTO',()=>{
 const f=readRepository();f.delete(NEW+'common/dto/PageParamDTO.java');
 assert.match(errors(f),/missing Platform/);
});
test('rejects missing enum',()=>{
 const f=readRepository();f.delete(NEW+'common/enums/resource/ControlLevelCode.java');
 assert.match(errors(f),/missing Platform/);
});
test('rejects changed legacy DTO FQCN',()=>{
 const f=readRepository(),p=NEW+'common/dto/user/UserDTO.java';
 f.set(p,f.get(p).replace('package io.yak.framework.security.common.dto.user;','package io.other;'));
 assert.match(errors(f),/legacy API FQCN/);
});
test('rejects old DAO import in new DTO',()=>{
 const f=readRepository(),p=NEW+'common/dto/user/UserDTO.java';
 f.set(p,f.get(p)+'\nimport io.yak.framework.security.dao.UserDao;\n');
 assert.match(errors(f),/must not import/);
});
test('rejects old Framework Common dependency in response',()=>{
 const f=readRepository(),p=NEW+'common/vo/user/UserVO.java';
 f.set(p,f.get(p)+'\nimport io.yak.framework.common.Result;\n');
 assert.match(errors(f),/must not import/);
});
test('rejects missing login DTO validation',()=>{
 const f=readRepository(),p=NEW+'common/dto/account/AccountLoginDTO.java';
 f.set(p,f.get(p).replaceAll('@NotBlank',''));
 assert.match(errors(f),/login DTO/);
});
test('rejects login password JSON field name drift',()=>{
 const f=readRepository(),p=NEW+'common/dto/account/AccountLoginDTO.java';
 f.set(p,f.get(p).replace('private String pw;','private String password;'));
 assert.match(errors(f),/login DTO/);
});
test('rejects loss of Project identity in user response',()=>{
 const f=readRepository(),p=NEW+'common/vo/user/CurrentUserVO.java';
 f.set(p,f.get(p).replace('private List<ProjectBriefVO> projectList','private List<ProjectBriefVO> removed'));
 assert.match(errors(f),/Project\/RBAC/);
});
test('rejects role permission tree null field serialization drift',()=>{
 const f=readRepository(),p=NEW+'common/vo/role/RoleVO.java';
 f.set(p,f.get(p).replace('JsonInclude.Include.NON_NULL','JsonInclude.Include.ALWAYS'));
 assert.match(errors(f),/role permission JSON/);
});
test('rejects pagination default drift',()=>{
 const f=readRepository(),p=NEW+'common/dto/PageParamDTO.java';
 f.set(p,f.get(p).replace('private int size = 10;','private int size = 100;'));
 assert.match(errors(f),/paging DTO default/);
});
test('rejects removed Bean Validation API dependency',()=>{
 const f=readRepository(),p='data-ops-platform/data-ops-platform-security-contract/pom.xml';
 f.set(p,f.get(p).replace('<artifactId>jakarta.validation-api</artifactId>','<artifactId>absent</artifactId>'));
 assert.match(errors(f),/dependency missing: jakarta.validation-api/);
});
test('rejects removed Jackson annotation dependency',()=>{
 const f=readRepository(),p='data-ops-platform/data-ops-platform-security-contract/pom.xml';
 f.set(p,f.get(p).replace('<artifactId>jackson-annotations</artifactId>','<artifactId>absent</artifactId>'));
 assert.match(errors(f),/dependency missing: jackson-annotations/);
});
test('rejects circular Starter dependency',()=>{
 const f=readRepository(),p='data-ops-platform/data-ops-platform-security-contract/pom.xml';
 f.set(p,f.get(p).replace('</dependencies>','<dependency><artifactId>data-security-spring-boot-starter</artifactId></dependency></dependencies>'));
 assert.match(errors(f),/cannot reverse depend/);
});
test('rejects Starter dropping its Platform contract dependency',()=>{
 const f=readRepository(),p='data-ops-framework/data-security/pom.xml';
 f.set(p,f.get(p).replace('<artifactId>data-ops-platform-security-contract</artifactId>','<artifactId>absent</artifactId>'));
 assert.match(errors(f),/Starter must consume/);
});

test('rejects removal of test-only Jackson databind for JSON wire regressions',()=>{
  const f=readRepository(),p='data-ops-platform/data-ops-platform-security-contract/pom.xml';
  f.set(p,f.get(p).replace('<artifactId>jackson-databind</artifactId>',
    '<artifactId>removed-json-test-support</artifactId>'));
  assert.match(errors(f),/dependency missing: jackson-databind/);
});
