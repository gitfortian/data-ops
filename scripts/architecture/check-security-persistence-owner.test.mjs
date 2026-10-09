import test from 'node:test';
import assert from 'node:assert/strict';
import { OWNED, readRepository, validatePersistenceOwners }
  from './check-security-persistence-owner.mjs';
const O='data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const N='data-ops-platform/data-ops-platform-security-persistence/src/main/java/io/yak/framework/security/';
const diagnostic=f=>validatePersistenceOwners(f).join('\n');
test('all 36 persistence models and mappers have exactly one Platform owner',()=>{
 assert.equal(OWNED.length,36);
 assert.deepEqual(validatePersistenceOwners(readRepository()),[]);
});
test('refuses duplicate old UserPO binary owner',()=>{
 const f=readRepository(),p='common/po/UserPO.java';
 f.set(O+p,f.get(N+p));assert.match(diagnostic(f),/Starter duplicate persistence/);
});
test('refuses absent PermissionMapper',()=>{
 const f=readRepository();f.delete(N+'dao/mapper/PermissionMapper.java');
 assert.match(diagnostic(f),/missing persistence Platform/);
});
test('refuses FQCN or package migration',()=>{
 const f=readRepository(),p=N+'common/po/RolePO.java';
 f.set(p,f.get(p).replace('package io.yak.framework.security.common.po;', 'package unrelated;'));
 assert.match(diagnostic(f),/legacy persistence FQCN/);
});
test('refuses persistence adapter importing business code',()=>{
 const f=readRepository(),p=N+'common/po/RolePO.java';
 f.set(p,f.get(p)+'\nimport io.yak.ops.business.security.User;\n');
 assert.match(diagnostic(f),/imports upper layer/);
});
test('refuses loss of app_name insert fill',()=>{
 const f=readRepository(),p=N+'common/po/AppBasePO.java';
 f.set(p,f.get(p).replace('FieldFill.INSERT','FieldFill.UPDATE'));
 assert.match(diagnostic(f),/app_name isolation/);
});
test('refuses loss of soft-deletion contract',()=>{
 const f=readRepository(),p=N+'common/po/BasePO.java';
 f.set(p,f.get(p).replace('@TableLogic(value = "0", delval = "1")',''));
 assert.match(diagnostic(f),/logical deletion/);
});
test('refuses password/salt being emitted in UserPO debug logs',()=>{
 const f=readRepository(),p=N+'common/po/UserPO.java';
 f.set(p,f.get(p).replaceAll('@ToString.Exclude',''));
 assert.match(diagnostic(f),/credential masking/);
});
test('refuses renaming the historical permission table',()=>{
 const f=readRepository(),p=N+'common/po/PermissionPO.java';
 f.set(p,f.get(p).replace('yak_security_permission','new_permission_table'));
 assert.match(diagnostic(f),/table identity/);
});
test('refuses numeric PG boolean mapping drift',()=>{
 const f=readRepository(),p=N+'config/NumericBooleanTypeHandler.java';
 f.set(p,f.get(p).replace('statement.setInt(index, value ? 1 : 0)','statement.setBoolean(index, value)'));
 assert.match(diagnostic(f),/numeric boolean/);
});
test('refuses removal of appName insert fill',()=>{
 const f=readRepository(),p=N+'config/YakSecurityMetaObjectHandler.java';
 f.set(p,f.get(p).replace('strictInsertFill(', 'skipInsert('));
 assert.match(diagnostic(f),/application name insertion/);
});
test('refuses Mapper annotation removing Spring-managed mapper scanning',()=>{
 const f=readRepository(),p=N+'dao/mapper/UserMapper.java';
 f.set(p,f.get(p).replace('@Mapper',''));
 assert.match(diagnostic(f),/Mapper identity/);
});
test('refuses a reverse dependency to Starter',()=>{
 const f=readRepository(),p='data-ops-platform/data-ops-platform-security-persistence/pom.xml';
 f.set(p,f.get(p).replace('</dependencies>',
 '<dependency><artifactId>data-security-spring-boot-starter</artifactId></dependency></dependencies>'));
 assert.match(diagnostic(f),/must not reverse/);
});
test('refuses loss of Reactor registration',()=>{
 const f=readRepository(),p='pom.xml';
 f.set(p,f.get(p).replace('<module>data-ops-platform/data-ops-platform-security-persistence</module>',''));
 assert.match(diagnostic(f),/Reactor, BOM and Starter/);
});
test('refuses changing historical Flyway location in Starter',()=>{
 const f=readRepository(),p=O+'config/DataSourceConfig.java';
 f.set(p,f.get(p).replace('classpath:yak-security/db/migration','classpath:wrong/location'));
 assert.match(diagnostic(f),/Flyway history/);
});

const ds=O+'config/DataSourceConfig.java';
function mutateDatasource(replaceFrom,replaceWith) {
 const f=readRepository();
 assert.ok(f.get(ds).includes(replaceFrom),'fixture must match actual datasource');
 f.set(ds,f.get(ds).replace(replaceFrom,replaceWith));
 return diagnostic(f);
}
test('refuses app_name tenant field drift',()=>{
 assert.match(mutateDatasource('return "app_name";','return "tenant_id";'),/tenant app_name/);
});
test('refuses missing pagination after tenant isolation',()=>{
 assert.match(
   mutateDatasource('new PaginationInnerInterceptor()', 'new TenantLineInnerInterceptor(tenantLineHandler)'),
   /interceptor order|tenant app_name/
 );
});
test('refuses Flyway historical baseline regression',()=>{
 assert.match(mutateDatasource('MigrationVersion.fromVersion("0")','MigrationVersion.fromVersion("1")'),/historical Security Flyway/);
});
test('refuses dropping Flyway dependency before SqlSessionFactory',()=>{
 assert.match(mutateDatasource('@DependsOn("yakSecurityFlyway")','@DependsOn("unrelated")'),/SqlSessionFactory/);
});
test('refuses losing MyBatis global metadata fill configuration',()=>{
 assert.match(mutateDatasource('factory.setGlobalConfig(globalConfig)','// intentionally absent'),/SqlSessionFactory/);
});
test('refuses missing PostgreSQL primitive boolean handler',()=>{
 assert.match(mutateDatasource('register(boolean.class, NumericBooleanTypeHandler.class)','register(int.class, NumericBooleanTypeHandler.class)'),/boolean type handlers/);
});
