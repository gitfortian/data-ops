import test from 'node:test';
import assert from 'node:assert/strict';
import { readRepository, checkDbMatrix } from './check-security-rbac-db-matrix.mjs';

test('A8.2j both SQL vendors have live executable Flyway/JDBC checks',()=>{
  assert.deepEqual(checkDbMatrix(readRepository()),[]);
});
test('A8.2j refuses dropping PostgreSQL service',()=>{
  const files=readRepository();
  files.ci=files.ci.replace('image: postgres:16','image: omitted');
  assert.match(checkDbMatrix(files).join('\n'),/real MySQL and PostgreSQL/);
});
test('A8.2j refuses loss of MySQL schema bootstrap service',()=>{
  const files=readRepository();
  files.ci=files.ci.replace('image: mysql:8.0','image: omitted');
  assert.match(checkDbMatrix(files).join('\n'),/real MySQL and PostgreSQL/);
});
test('A8.2j refuses dropping dedicated role-grant data assertion',()=>{
  const files=readRepository();
  files.test=files.test.replaceAll('yak_security_role_permission','lost_table');
  assert.match(checkDbMatrix(files).join('\n'),/role grants/);
});
test('A8.2j refuses removing transaction rollback evidence',()=>{
  const files=readRepository();
  files.test=files.test.replace('connection.rollback()','connection.commit()');
  assert.match(checkDbMatrix(files).join('\n'),/exercise rollback/);
});
test('A8.2j refuses altering Security baseline version',()=>{
  const files=readRepository();
  files.source=files.source.replace('MigrationVersion.fromVersion("0")',
      'MigrationVersion.fromVersion("1")');
  assert.match(checkDbMatrix(files).join('\n'),/baseline configuration/);
});
