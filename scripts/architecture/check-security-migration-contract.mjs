#!/usr/bin/env node
/**
 * A8.2a pre-migration freeze. Structural guards complement (never replace)
 * Security integration tests and real historical Flyway upgrade rehearsal.
 */
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
export const PATHS = Object.freeze({
  imports: 'data-ops-framework/data-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports',
  auto: 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/autoconfigure/YakSecurityAutoConfiguration.java',
  database: 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/autoconfigure/YakSecurityDatabaseConfiguration.java',
  datasource: 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/config/DataSourceConfig.java',
  properties: 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/config/YakSecurityProperties.java',
  mysql: 'data-ops-framework/data-security/src/main/resources/yak-security/db/migration/V1__security_framework_baseline.sql',
  postgres: 'data-ops-framework/data-security/src/main/resources/yak-security/db/migration-postgresql/V1__security_framework_baseline.sql',
});
const REQUIRE = [
  ['imports', /^(?:io\.yak\.framework\.security\.autoconfigure\.YakSecurityAutoConfiguration)\s*$/m, 'single Security auto-configuration entry'],
  ['auto', /@EnableConfigurationProperties\(YakSecurityProperties\.class\)/, 'configuration properties binding'],
  ['auto', /YakSecurityDatabaseConfiguration\.class/, 'database configuration import'],
  ['auto', /YakSecurityWebConfiguration\.class/, 'web configuration import'],
  ['auto', /YakSecurityAuditConfiguration\.class/, 'audit configuration import'],
  ['auto', /@ConditionalOnMissingBean\(PasswordEncoder\.class\)/, 'password encoder override hook'],
  ['database', /@ConditionalOnProperty\(prefix = "yak\.security", name = "database-enabled", havingValue = "true", matchIfMissing = true\)/, 'database-enabled compatibility default'],
  ['datasource', /@Bean\s*\(\s*name\s*=\s*"yakSecurityDataSource"/, 'independent Security datasource bean'],
  ['datasource', /"yakSecuritySqlSessionTemplate"/, 'independent MyBatis session bean'],
  ['datasource', /"yakSecurityTransactionManager"/, 'independent transaction manager'],
  ['datasource', /classpath:yak-security\/db\/migration/, 'Security Flyway location'],
  ['datasource', /TenantLineInnerInterceptor/, 'tenant isolation interceptor'],
  ['properties', /private boolean enabled = true/, 'Security enabled default'],
  ['properties', /private boolean databaseEnabled = true/, 'Security database default'],
  ['properties', /private boolean webEnabled = true/, 'Security web default'],
  ['properties', /private boolean authenticationEnabled = true/, 'authentication default'],
  ['properties', /\/yak-security\/api\/v1\/account\/login/, 'public login endpoint'],
  ['mysql', /CREATE TABLE IF NOT EXISTS yak_security_project\b/i, 'MySQL Project history'],
  ['mysql', /CREATE TABLE IF NOT EXISTS yak_security_permission\b/i, 'MySQL permission history'],
  ['postgres', /CREATE TABLE "yak_security_project"/i, 'PostgreSQL Project history'],
  ['postgres', /CREATE TABLE "yak_security_permission"/i, 'PostgreSQL permission history'],
];
export function checkSecurityContract(files) {
  const errors = [];
  for (const [key, pattern, label] of REQUIRE) {
    if (!pattern.test(files[key] ?? '')) errors.push(key + ': missing ' + label);
  }
  const entries = (files.imports ?? '').split(/\r?\n/).map(s => s.trim())
    .filter(s => s && !s.startsWith('#'));
  if (entries.length !== 1 || new Set(entries).size !== 1) {
    errors.push('imports: expected exactly one auto-configuration registration');
  }
  return errors;
}
export function readSecurityFiles(root = ROOT) {
  return Object.fromEntries(Object.entries(PATHS).map(([key, path]) =>
    [key, readFileSync(resolve(root, path), 'utf8')]));
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const errors = checkSecurityContract(readSecurityFiles());
    if (errors.length) { console.error(errors.join('\n')); process.exitCode = 1; }
    else console.log('A8.2a Security entrypoints, persistence and defaults freeze passed.');
  } catch (error) {
    console.error('A8.2a freeze failed closed: ' + error.message);
    process.exitCode = 1;
  }
}
