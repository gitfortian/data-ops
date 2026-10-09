#!/usr/bin/env node
/** A8.2s Security persistence owner and historic DB compatibility gate. */
import { readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const OLD = 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const NEW = 'data-ops-platform/data-ops-platform-security-persistence/src/main/java/io/yak/framework/security/';
const NEW_POM = 'data-ops-platform/data-ops-platform-security-persistence/pom.xml';
const STARTER_POM = 'data-ops-framework/data-security/pom.xml';
const DATA_SOURCE = OLD + 'config/DataSourceConfig.java';
export const OWNED = Object.freeze([
  "common/po/AppBasePO.java",
  "common/po/BasePO.java",
  "common/po/ConfigPO.java",
  "common/po/DeptPO.java",
  "common/po/MenuPO.java",
  "common/po/MessagePO.java",
  "common/po/OplogExtraPO.java",
  "common/po/OplogPO.java",
  "common/po/PermissionPO.java",
  "common/po/ProjectPO.java",
  "common/po/ResourceTypePO.java",
  "common/po/RoleMenuPO.java",
  "common/po/RolePO.java",
  "common/po/RolePermissionPO.java",
  "common/po/UserPO.java",
  "common/po/UserProjectPO.java",
  "common/po/UserResourcePO.java",
  "common/po/UserRolePO.java",
  "config/NumericBooleanTypeHandler.java",
  "config/YakSecurityMetaObjectHandler.java",
  "dao/mapper/ConfigMapper.java",
  "dao/mapper/DeptMapper.java",
  "dao/mapper/MenuMapper.java",
  "dao/mapper/MessageMapper.java",
  "dao/mapper/OplogExtraMapper.java",
  "dao/mapper/OplogMapper.java",
  "dao/mapper/PermissionMapper.java",
  "dao/mapper/ProjectMapper.java",
  "dao/mapper/ResourceTypeMapper.java",
  "dao/mapper/RoleMapper.java",
  "dao/mapper/RoleMenuMapper.java",
  "dao/mapper/RolePermissionMapper.java",
  "dao/mapper/UserMapper.java",
  "dao/mapper/UserProjectMapper.java",
  "dao/mapper/UserResourceMapper.java",
  "dao/mapper/UserRoleMapper.java"
]);

export function readRepository(root=ROOT) {
 const paths = ['pom.xml', 'data-ops-bom/pom.xml', NEW_POM, STARTER_POM, DATA_SOURCE,
   ...OWNED.flatMap(f=>[OLD+f,NEW+f])];
 const sources=new Map();
 for(const path of paths) {
   const full=resolve(root,path);
   if(existsSync(full)) sources.set(path,readFileSync(full,'utf8'));
 }
 return sources;
}
export function validatePersistenceOwners(files) {
 const errors=[];
 if(OWNED.length!==36 || new Set(OWNED).size!==36) {
   errors.push('expected exactly 36 unique Security Persistence types');
 }
 for(const name of OWNED) {
   if(files.has(OLD+name)) errors.push('Starter duplicate persistence class: '+name);
   const java=files.get(NEW+name);
   if(!java) {errors.push('missing persistence Platform owner: '+name);continue;}
   const pkg='io.yak.framework.security.'+
       name.substring(0,name.lastIndexOf('/')).replaceAll('/','.');
   if(!java.includes('package '+pkg+';')) errors.push('legacy persistence FQCN changed: '+name);
   if(/^\s*import\s+(?:io\.yak\.framework\.common|io\.yak\.framework\.security\.(?:autoconfigure|controller|service\.impl)|io\.yak\.ops\.(?:business|boot|common)|org\.flywaydb)\b/m.test(java)) {
     errors.push('persistence adapter imports upper layer, old Common or Flyway: '+name);
   }
 }
 const metadata=files.get(NEW+'common/po/AppBasePO.java')||'';
 const base=files.get(NEW+'common/po/BasePO.java')||'';
 const user=files.get(NEW+'common/po/UserPO.java')||'';
 const permission=files.get(NEW+'common/po/PermissionPO.java')||'';
 const handler=files.get(NEW+'config/NumericBooleanTypeHandler.java')||'';
 const metaFill=files.get(NEW+'config/YakSecurityMetaObjectHandler.java')||'';
 if(!metadata.includes('FieldFill.INSERT') || !metadata.includes('private String appName;') ||
    !base.includes('@TableLogic(value = "0", delval = "1")') ||
    !base.includes('@TableId(type = IdType.AUTO)')) {
   errors.push('app_name isolation, auto IDs or logical deletion mapping changed');
 }
 if(!user.includes('@TableName("yak_security_user")') ||
    !user.includes('@ToString.Exclude') || !user.includes('private String pw;') ||
    !permission.includes('@TableName("yak_security_permission")')) {
   errors.push('security persistence table identity or credential masking changed');
 }
 if(!handler.includes('extends BooleanTypeHandler') ||
    !handler.includes('statement.setInt(index, value ? 1 : 0)')) {
   errors.push('PostgreSQL numeric boolean mapping changed');
 }
 if(!metaFill.includes('strictInsertFill(') || !metaFill.includes('"appName"')) {
   errors.push('Security application name insertion metadata changed');
 }
 for(const name of OWNED.filter(p=>p.startsWith('dao/mapper/'))) {
   const java=files.get(NEW+name)||'';
   if(!java.includes('@Mapper') || !java.includes('extends BaseMapper<')) {
     errors.push('Mapper identity, Spring binding or MyBatis contract changed: '+name);
   }
 }
 const rootPom=files.get('pom.xml')||'';
 const bom=files.get('data-ops-bom/pom.xml')||'';
 const starter=files.get(STARTER_POM)||'';
 const persistence=files.get(NEW_POM)||'';
 if(!rootPom.includes('<module>data-ops-platform/data-ops-platform-security-persistence</module>') ||
    !bom.includes('<artifactId>data-ops-platform-security-persistence</artifactId>') ||
    !starter.includes('<artifactId>data-ops-platform-security-persistence</artifactId>')) {
   errors.push('root Reactor, BOM and Starter must resolve single Persistence owner');
 }
 if(!persistence.includes('<artifactId>mybatis-plus-core</artifactId>') ||
    !persistence.includes('<artifactId>lombok</artifactId>')) {
   errors.push('Security persistence adapter is missing MyBatis / bean mapping ABI dependencies');
 }
 if(/<artifactId>(?:data-security-spring-boot-starter|data-ops-(?:business|boot|common)[^<]*)<\/artifactId>/.test(persistence)) {
   errors.push('Persistence must not reverse depend on Starter or product');
 }
 const datasource=files.get(DATA_SOURCE)||'';
 if(!datasource.includes('@MapperScan(') ||
    !datasource.includes('io.yak.framework.security.dao.mapper') ||
    !datasource.includes('yakSecuritySqlSessionTemplate') ||
    !datasource.includes('classpath:yak-security/db/migration') ||
    !datasource.includes('yakSecurityTransactionManager')) {
   errors.push('Starter must preserve MapperScan, Flyway history and named transaction assembly');
 }
 return errors;
}
if(process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 try {
   const failures=validatePersistenceOwners(readRepository());
   if(failures.length) {console.error(failures.join('\n'));process.exitCode=1;}
   else console.log('A8.2s 36 Security Persistence class owners and DB compatibility passed');
 }catch(error){console.error('A8.2s failed closed: '+error.message);process.exitCode=1;}
}
