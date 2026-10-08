/**
 * Statically inventories domain-owned Flyway chains without running migrations.
 * Runtime wiring/dynamic expressions are outside this narrow, fail-closed contract.
 */
import path from 'node:path';

const JAVA = '/src/main/java/';
const MIGRATION = '/src/main/resources/db/';
const DOUBLE_QUOTED = /^"([^"]+)"$/;
const IDENTIFIER = /^[A-Za-z_$][\w$]*$/;

function stringConstant(source, expression) {
  const trimmed = expression.trim();
  const literal = trimmed.match(DOUBLE_QUOTED);
  if (literal) return literal[1];
  if (!IDENTIFIER.test(trimmed)) return null;
  const constant = [...source.matchAll(/\b(?:static\s+)?final\s+String\s+([A-Za-z_$][\w$]*)\s*=\s*"([^"]+)"\s*;/g)]
    .find((match) => match[1] === trimmed);
  return constant?.[2] ?? null;
}

export function parseFlywayRegistrations(file, source) {
  if (!file.includes(JAVA) || file.startsWith('data-ops-framework/legacy/')) return [];
  const owner = file.slice(0, file.indexOf(JAVA));
  const declarations = [...source.matchAll(/\bpublic\s+Flyway\s+([A-Za-z_$][\w$]*)\s*\(/g)];
  const registrations = [];
  for (const [index, declaration] of declarations.entries()) {
    const method = source.slice(declaration.index, declarations[index + 1]?.index ?? source.length);
    if (!method.includes('Flyway.configure()')) continue;
    // Bean ownership must be explicit; helper methods are not a migration registration.
    const before = source.slice(Math.max(0, declaration.index - 320), declaration.index);
    if (!/@Bean\b/.test(before)) continue;
    const tableExpression = method.match(/\.table\s*\(\s*([^()]+?)\s*\)/)?.[1] ?? '';
    const locationExpression = method.match(/JdbcDatabase\.migrationLocation\s*\(\s*[^,()]+\s*,\s*([^()]+?)\s*\)/)?.[1] ?? '';
    const table = stringConstant(source, tableExpression);
    const location = stringConstant(source, locationExpression);
    const beanName = before.match(/@Bean\s*\(\s*(?:name\s*=\s*)?"([^"]+)"/)?.[1]
      ?? declaration[1];
    registrations.push({ file, owner, beanName, method: declaration[1], table, location });
  }
  return registrations;
}

export function analyzeFlywayOwnership(sources, fileNames) {
  const files = new Set(fileNames);
  const registrations = sources.flatMap(({ file, content }) => parseFlywayRegistrations(file, content))
    .sort((a, b) => a.file.localeCompare(b.file) || a.method.localeCompare(b.method));
  const violations = [];
  const tables = new Map();
  const locations = new Map();
  const beans = new Map();
  const qualifiedLocations = new Map();

  for (const registration of registrations) {
    const { file, owner, method, table, location, beanName } = registration;
    if (!table || !location) {
      violations.push(file + ':' + method + ': Flyway table/location must be a local string literal or static final String; dynamic ownership needs explicit review');
      continue;
    }
    if (!/^[a-z][a-z0-9_]*$/.test(table)) {
      violations.push(file + ':' + method + ': invalid Flyway history table: ' + table);
    }
    if (!/^classpath:db\/migration\/[a-z0-9-]+$/.test(location)) {
      violations.push(file + ':' + method + ': invalid Flyway migration namespace: ' + location);
      continue;
    }

    const prefix = location.slice('classpath:db/'.length);
    // Vendor selection is performed by JdbcDatabase.migrationLocation; both sibling
    // folders must be present. Baseline history checksum is checked separately.
    for (const vendor of ['migration', 'migration-postgresql']) {
      const local = prefix.replace(/^migration\//, vendor + '/');
      const expectedDir = path.posix.join(owner, 'src/main/resources/db', local);
      if (![...files].some((f) => f.startsWith(expectedDir + '/') && /\.sql$/.test(f))) {
        violations.push(file + ':' + method + ': missing local ' + vendor + ' scripts at ' + expectedDir);
      }
    }

    for (const [name, value, found] of [['history table', table, tables], ['migration namespace', location, locations], ['Spring Bean', beanName, beans]]) {
      const previous = found.get(value);
      if (previous && previous.file !== file) {
        violations.push(file + ':' + method + ': duplicate ' + name + ' ' + value + ' also declared by ' + previous.file);
      } else if (previous && previous.method !== method) {
        violations.push(file + ':' + method + ': duplicate ' + name + ' ' + value + ' in ' + previous.method);
      } else {
        found.set(value, registration);
      }
    }
    qualifiedLocations.set(location, owner);
  }
  return { registrations, violations: [...new Set(violations)].sort(), inspected: sources.length };
}
