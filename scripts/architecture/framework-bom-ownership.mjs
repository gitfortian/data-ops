import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/** A8.1b: Data-Ops BOM owns the six third-party versions previously inherited from Framework. */
export const THIRD_PARTY = Object.freeze([
  ['com.baomidou', 'mybatis-plus-core', 'mybatis-plus.version', '3.5.16'],
  ['com.baomidou', 'mybatis-plus-spring-boot3-starter', 'mybatis-plus.version', '3.5.16'],
  ['com.baomidou', 'mybatis-plus-jsqlparser-4.9', 'mybatis-plus.version', '3.5.16'],
  ['com.alibaba', 'druid', 'druid.version', '1.2.28'],
  ['org.checkerframework', 'checker-qual', 'checker-qual.version', '3.37.0'],
  ['org.springdoc', 'springdoc-openapi-starter-webmvc-ui', 'springdoc.version', '2.6.0'],
]);

const clean = xml => xml.replace(/<!--[\s\S]*?-->/g, '');
const property = (xml, name) => {
  // Properties are known fixed names (a-z, dots and hyphens only).
  const escaped = name.replace(/\./g, '\\.');
  return xml.match(new RegExp('<' + escaped + '>\\s*([^<]+?)\\s*</' + escaped + '>'))?.[1]?.trim();
};
function managed(xml) {
  const content = xml.match(/<dependencyManagement>([\s\S]*?)<\/dependencyManagement>/)?.[1] || '';
  return [...content.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)].map(m => ({
    group: property(m[1], 'groupId'),
    artifact: property(m[1], 'artifactId'),
    version: property(m[1], 'version'),
    type: property(m[1], 'type'),
    scope: property(m[1], 'scope'),
  }));
}
export function verifyBomOwnership(raw) {
  const xml = clean(raw), dependencies = managed(xml);
  const problems = [];
  if (dependencies.some(d => d.group === 'io.github.weifuwan'
    && d.artifact === 'data-ops-framework-parent')) {
    problems.push('BOM must not import or manage old Framework parent');
  }
  const props = xml.match(/<properties>([\s\S]*?)<\/properties>/)?.[1] || '';
  for (const [group, artifact, prop, version] of THIRD_PARTY) {
    const rows = dependencies.filter(d => d.group === group && d.artifact === artifact);
    if (rows.length !== 1) {
      problems.push(group + ':' + artifact + ': expected exactly one managed coordinate, got ' + rows.length);
      continue;
    }
    if (property(props, prop) !== version) {
      problems.push(prop + ': expected ' + version + ', got ' + (property(props, prop) || 'missing'));
    }
    if (rows[0].version !== '$' + '{' + prop + '}' || rows[0].type || rows[0].scope) {
      problems.push(group + ':' + artifact + ': wrong version reference or scope');
    }
  }
  return problems;
}
export function verifyParity(bomRaw, frameworkRaw) {
  const b = clean(bomRaw), f = clean(frameworkRaw);
  const bm = managed(b), fm = managed(f);
  const problems = verifyBomOwnership(b);
  const bp = b.match(/<properties>([\s\S]*?)<\/properties>/)?.[1] || '';
  const fp = f.match(/<properties>([\s\S]*?)<\/properties>/)?.[1] || '';
  for (const [group, artifact, prop] of THIRD_PARTY) {
    const expectedRef = '$' + '{' + prop + '}';
    const old = fm.find(d => d.group === group && d.artifact === artifact);
    const fresh = bm.find(d => d.group === group && d.artifact === artifact);
    if (!old || !fresh || old.version !== expectedRef || fresh.version !== old.version
      || property(fp, prop) !== property(bp, prop)) {
      problems.push('Framework/BOM parity mismatch for ' + group + ':' + artifact);
    }
  }
  return [...new Set(problems)];
}
export function readCurrent() {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
  return {
    bom: readFileSync(resolve(root, 'data-ops-bom/pom.xml'), 'utf8'),
    parent: readFileSync(resolve(root, 'data-ops-framework/pom.xml'), 'utf8'),
  };
}
