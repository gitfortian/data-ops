#!/usr/bin/env node
/** A8.2r: frozen single binary owner of 75 Security API request/response types. */
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
const ROOT=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const OLD='data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const NEW='data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/';
const POM='data-ops-platform/data-ops-platform-security-contract/pom.xml';
const STARTER='data-ops-framework/data-security/pom.xml';
export const CONTRACT_TYPES=Object.freeze([
  "common/constant/Constants.java",
  "common/constant/FieldConstant.java",
  "common/constant/OplogConstant.java",
  "common/dto/PageParamDTO.java",
  "common/dto/account/AccountLoginDTO.java",
  "common/dto/config/ConfigDTO.java",
  "common/dto/config/ConfigQueryDTO.java",
  "common/dto/dept/DeptDTO.java",
  "common/dto/dept/DeptSaveDTO.java",
  "common/dto/message/MessageBatchReadDTO.java",
  "common/dto/message/MessageDTO.java",
  "common/dto/message/MessagePageQueryDTO.java",
  "common/dto/message/MessageReadDTO.java",
  "common/dto/oplog/OplogDTO.java",
  "common/dto/oplog/OplogQueryDTO.java",
  "common/dto/permission/PermissionDTO.java",
  "common/dto/project/ProjectBriefQueryDTO.java",
  "common/dto/project/ProjectQueryDTO.java",
  "common/dto/project/ProjectSaveDTO.java",
  "common/dto/project/ProjectStatusDTO.java",
  "common/dto/project/ProjectUserAssignDTO.java",
  "common/dto/resource/AssignToManyUserDTO.java",
  "common/dto/resource/AssignToOneUserDTO.java",
  "common/dto/resource/BatchAssignDTO.java",
  "common/dto/resource/ControlLevelQueryDTO.java",
  "common/dto/resource/MByRDataQueryDTO.java",
  "common/dto/resource/MByRQueryDTO.java",
  "common/dto/resource/MByUDataQueryDTO.java",
  "common/dto/resource/MByUQueryDTO.java",
  "common/dto/resource/ResourceDTO.java",
  "common/dto/resource/ResourceViewControlDTO.java",
  "common/dto/resource/UserResourceQueryDTO.java",
  "common/dto/resource/type/ResourceTypeQueryDTO.java",
  "common/dto/role/RoleAssignDTO.java",
  "common/dto/role/RoleQueryDTO.java",
  "common/dto/role/RoleSaveDTO.java",
  "common/dto/user/UserBriefQueryDTO.java",
  "common/dto/user/UserDTO.java",
  "common/dto/user/UserPasswordResetDTO.java",
  "common/dto/user/UserQueryDTO.java",
  "common/enums/ConfigStatusEnum.java",
  "common/enums/message/MessageCode.java",
  "common/enums/oplog/OplogCode.java",
  "common/enums/project/ProjectUserCode.java",
  "common/enums/resource/ControlLevelCode.java",
  "common/enums/resource/HasLevelCode.java",
  "common/enums/resource/ShowLevelCode.java",
  "common/enums/user/UserCheckType.java",
  "common/vo/config/ConfigVO.java",
  "common/vo/dept/DeptBriefVO.java",
  "common/vo/dept/DeptDeleteCheckVO.java",
  "common/vo/dept/DeptTreeVO.java",
  "common/vo/dept/DeptVO.java",
  "common/vo/message/MessagePageVO.java",
  "common/vo/message/MessageVO.java",
  "common/vo/oplog/OplogOptionsVO.java",
  "common/vo/oplog/OplogVO.java",
  "common/vo/permission/PermissionTreeVO.java",
  "common/vo/project/ProjectBriefVO.java",
  "common/vo/project/ProjectBriefVOWithUser.java",
  "common/vo/project/ProjectDeleteCheckVO.java",
  "common/vo/project/ProjectVO.java",
  "common/vo/resource/MByRDataVO.java",
  "common/vo/resource/MByRVO.java",
  "common/vo/resource/MByUDataVO.java",
  "common/vo/resource/MByUVO.java",
  "common/vo/resource/ResourceTypeVO.java",
  "common/vo/role/AssignInfoVO.java",
  "common/vo/role/RoleBriefVO.java",
  "common/vo/role/RoleDeleteCheckVO.java",
  "common/vo/role/RoleVO.java",
  "common/vo/user/CurrentUserVO.java",
  "common/vo/user/UserBasicVO.java",
  "common/vo/user/UserBriefVO.java",
  "common/vo/user/UserVO.java"
]);
export function readRepository(root=ROOT) {
 const files=new Map();
 for(const path of [POM,STARTER,...CONTRACT_TYPES.flatMap(p=>[OLD+p,NEW+p])]) {
   const full=resolve(root,path);
   if(existsSync(full)) files.set(path,readFileSync(full,'utf8'));
 }
 return files;
}
export function validateApiContracts(files) {
 const errors=[];
 if(CONTRACT_TYPES.length!==75||new Set(CONTRACT_TYPES).size!==75) errors.push('API manifest cardinality invalid');
 for(const name of CONTRACT_TYPES) {
   if(files.has(OLD+name)) errors.push('duplicate Starter DTO/VO owner: '+name);
   const body=files.get(NEW+name);
   if(!body) { errors.push('missing Platform Security API source: '+name);continue; }
   const pkg='io.yak.framework.security.'+name.slice(0,name.lastIndexOf('/')).replaceAll('/','.');
   if(!body.includes('package '+pkg+';')) errors.push('legacy API FQCN drift: '+name);
   if(/^\s*import\s+(?:io\.yak\.framework\.common|io\.yak\.framework\.security\.(?:dao|controller|service|config|autoconfigure|common\.po)|io\.yak\.ops\.(?:boot|business|common)|cn\.dev33|org\.mybatis|com\.baomidou)\b/m.test(body)) errors.push('Platform DTO/VO must not import old Framework or business runtime: '+name);
 }
 const login=files.get(NEW+'common/dto/account/AccountLoginDTO.java')||'';
 if(!login.includes('@NotBlank')||!login.includes('private String pw;')||!login.includes('private String userName;')) errors.push('login DTO validation/field contract drift');
 const current=files.get(NEW+'common/vo/user/CurrentUserVO.java')||'';
 if(!current.includes('private List<String> permissionCodes')||!current.includes('private List<String> menuCodes')||!current.includes('private List<ProjectBriefVO> projectList')) errors.push('Project/RBAC current user response shape drift');
 const role=files.get(NEW+'common/vo/role/RoleVO.java')||'';
 if(!role.includes('JsonInclude.Include.NON_NULL')||!role.includes('private PermissionTreeVO permissionTreeVO')) errors.push('role permission JSON null semantics drift');
 const page=files.get(NEW+'common/dto/PageParamDTO.java')||'';
 if(!page.includes('private int page = 1;')||!page.includes('private int size = 10;')) errors.push('Security paging DTO default drift');
 const pom=files.get(POM)||'',starter=files.get(STARTER)||'';
 for(const dep of ['lombok','jakarta.validation-api','jackson-annotations']) if(!pom.includes('<artifactId>'+dep+'</artifactId>')) errors.push('API contract dependency missing: '+dep);
 if(/<artifactId>(?:data-security-spring-boot-starter|data-ops-common|data-ops-boot|data-ops-business-[^<]+)<\/artifactId>/.test(pom)) errors.push('Platform API cannot reverse depend on Starter/Business');
 if(!starter.includes('<artifactId>data-ops-platform-security-contract</artifactId>')) errors.push('Starter must consume canonical Platform DTO/VO');
 return errors;
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 try {
   const errors=validateApiContracts(readRepository());
   if(errors.length){console.error(errors.join('\n'));process.exitCode=1;}
   else console.log('A8.2r 75 Security API DTO/VO/enum source owners verified');
 }catch(error){console.error('A8.2r owner guard failed closed: '+error.message);process.exitCode=1;}
}
