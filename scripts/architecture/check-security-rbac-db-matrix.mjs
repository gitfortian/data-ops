#!/usr/bin/env node
/**
 * A8.2j: real DB compatibility gate must run on both supported SQL vendors.
 * It deliberately does not pretend to emulate every pre-consolidation Flyway history.
 */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
export const PATHS = Object.freeze({
  ci: '.github/workflows/architecture-checks.yml',
  test: 'data-ops-framework/data-security/src/test/java/io/yak/framework/security/config/SecurityProjectRbacFlywayJdbcTest.java',
  source: 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/config/DataSourceConfig.java',
  mysql: 'data-ops-framework/data-security/src/main/resources/yak-security/db/migration/V1__security_framework_baseline.sql',
  postgres: 'data-ops-framework/data-security/src/main/resources/yak-security/db/migration-postgresql/V1__security_framework_baseline.sql',
});
export function checkDbMatrix(files) {
  const errors = [];
  const ci=files.ci||'', test=files.test||'', source=files.source||'';
  // A separate A0–A8 preview also uses databases; it must never conceal
  // the loss of the real Security backend test matrix.
  const backend = ci.split('\n  backend:\n')[1]?.split('\n  frontend:\n')[0] || '';
  if (!backend.includes('image: mysql:8.0') || !backend.includes('image: postgres:16')
      || !backend.includes('ARCHITECTURE_MYSQL_URL') || !backend.includes('ARCHITECTURE_PG_URL')) {
    errors.push('Architecture CI must provide real MySQL and PostgreSQL backend containers');
  }
  if (!/mysqlHostSchemaBaselineRetainsProjectIsolationAndRbacGrant\(/.test(test)
      || !/postgresHostSchemaBaselineRetainsProjectIsolationAndRbacGrant\(/.test(test)
      || !test.includes('flyway.validateWithResult().validationSuccessful')
      || !test.includes('connection.rollback()')) {
    errors.push('Both JDBC Flyway tests must validate history and exercise rollback');
  }
  if (!test.includes('yak_security_user_project') ||
      !test.includes('yak_security_role_permission') ||
      !test.includes('user_type') || !test.includes('app_name')) {
    errors.push('JDBC tests must cover Project user type, app isolation and role grants');
  }
  if (!source.includes('yak-security/db/migration') ||
      !source.includes('migration-postgresql') ||
      !source.includes('MigrationVersion.fromVersion("0")') ||
      !source.includes('.outOfOrder(true)')) {
    errors.push('Historical Flyway migration discovery and baseline configuration must remain');
  }
  if (!(files.mysql||'').includes('CREATE TABLE IF NOT EXISTS yak_security_user_project')
      || !(files.mysql||'').includes('CREATE TABLE IF NOT EXISTS yak_security_role_permission')) {
    errors.push('Original MySQL consolidated Security baseline is missing');
  }
  if (!(files.postgres||'').includes('CREATE TABLE "yak_security_user_project"')
      || !(files.postgres||'').includes('CREATE TABLE "yak_security_role_permission"')) {
    errors.push('Original PostgreSQL consolidated Security baseline is missing');
  }
  return errors;
}
export function readRepository(root=ROOT) {
  return Object.fromEntries(Object.entries(PATHS).map(([key,path])=>
    [key,readFileSync(resolve(root,path),'utf8')]));
}
if (process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  try {
    const errors=checkDbMatrix(readRepository());
    if(errors.length) {
      console.error(errors.join('\n'));
      process.exitCode=1;
    } else {
      console.log('A8.2j MySQL/PostgreSQL JDBC regression gates are present.');
    }
  } catch(err) {
    console.error('A8.2j DB matrix failed closed: '+err.message);
    process.exitCode=1;
  }
}
