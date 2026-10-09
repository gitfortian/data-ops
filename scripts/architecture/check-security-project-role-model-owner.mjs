#!/usr/bin/env node
/** A8.2h: six stable Project/Role bean and query contracts have one Platform class owner. */
import { execFileSync } from 'node:child_process';
import { readFileSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const OLD = 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/';
const NEW = 'data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/';
export const MOVED = [
  'common/dto/user/UserProjectDTO.java',
  'common/entity/BaseEntity.java',
  'common/entity/project/Project.java',
  'common/entity/project/ProjectBrief.java',
  'common/entity/role/Role.java',
  'common/entity/role/RoleBrief.java',
];
const RETAINED = [
  'data-ops-framework/data-security/src/main/java/io/yak/framework/security/dao/ProjectDao.java',
  'data-ops-framework/data-security/src/main/java/io/yak/framework/security/dao/RoleDao.java',
  'data-ops-framework/data-security/src/main/java/io/yak/framework/security/dao/UserProjectDao.java',
  'data-ops-platform/data-ops-platform-security-contract/pom.xml',
];

export function validateModelOwners(files) {
  const errors = [];
  for (const path of MOVED) {
    if (files.has(OLD + path)) errors.push('duplicate Framework model: ' + path);
    const source = files.get(NEW + path);
    if (!source) {
      errors.push('missing Platform model: ' + path);
      continue;
    }
    const pkg = 'io.yak.framework.security.' + path.substring(0, path.lastIndexOf('/')).replaceAll('/', '.');
    if (!source.includes('package ' + pkg + ';')) errors.push('historic Java FQCN changed: ' + path);
    if (!source.includes('public class ' + path.split('/').at(-1).replace('.java', '') + ' ')
        && !source.includes('public class ' + path.split('/').at(-1).replace('.java', '') + '{')) {
      errors.push('historic public class signature changed: ' + path);
    }
    if (/io\.yak\.framework\.security\.(?:common\.po|dao|service|autoconfigure)\b/.test(source)
        || /com\.baomidou|org\.springframework\.transaction/.test(source)) {
      errors.push('Platform pure model must not reference database runtime: ' + path);
    }
  }
  const project = files.get(NEW + 'common/entity/project/Project.java') || '';
  const role = files.get(NEW + 'common/entity/role/Role.java') || '';
  const base = files.get(NEW + 'common/entity/BaseEntity.java') || '';
  const dto = files.get(NEW + 'common/dto/user/UserProjectDTO.java') || '';
  if (!project.includes('extends BaseEntity') || !role.includes('extends BaseEntity')
      || !base.includes('private Boolean isDelete = false;')) {
    errors.push('Project and Role inheritance / BaseEntity logical deletion contract changed');
  }
  for (const field of ['private Long userId;', 'private Integer userType;',
      'private Long projectId;', 'private Boolean isDelete;']) {
    if (!dto.includes(field)) errors.push('Project query DTO filter missing: ' + field);
  }
  if (!(files.get(RETAINED[0]) || '').includes('common.entity.project.Project')
      || !(files.get(RETAINED[1]) || '').includes('common.entity.role.Role')
      || !(files.get(RETAINED[2]) || '').includes('common.dto.user.UserProjectDTO')) {
    errors.push('Existing Project/Role DAO API must retain old FQCN imports');
  }
  const pom = files.get(RETAINED[3]) || '';
  if (!pom.includes('<artifactId>lombok</artifactId>')
      || /<artifactId>(?:data-security-spring-boot-starter|data-ops-business-[^<]+)<\/artifactId>/.test(pom)) {
    errors.push('Platform pure model dependencies must remain unidirectional');
  }
  return errors;
}

export function readRepository(root = ROOT) {
  const needed = new Set([
    ...MOVED.flatMap(path => [OLD + path, NEW + path]), ...RETAINED,
  ]);
  const gitPaths = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'],
      {cwd:root, encoding:'utf8', maxBuffer:16*1024*1024}).split('\0');
  const files = new Map();
  for (const path of gitPaths) {
    if (!needed.has(path)) continue;
    const full=resolve(root,path);
    if (existsSync(full)) files.set(path,readFileSync(full,'utf8'));
  }
  return files;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const errors=validateModelOwners(readRepository());
    if (errors.length) {
      console.error(errors.join('\n'));
      process.exitCode=1;
    } else {
      console.log('A8.2h six Project/Role Platform model owners validated.');
    }
  } catch (error) {
    console.error('A8.2h model ownership failed closed: '+error.message);
    process.exitCode=1;
  }
}
