import { crossesPersistenceBoundary } from './import-boundaries.mjs';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const files = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], {encoding:'utf8'}).split('\0').filter(p => p && fs.existsSync(p));
const read = p => fs.readFileSync(p, 'utf8');
const modules = [];
function reactor(pom) {
  const xml = read(pom);
  const directory = path.posix.dirname(pom);
  const artifact = xml.replace(/<parent>[\s\S]*?<\/parent>/g, '').match(/<artifactId>([^<]+)<\/artifactId>/)?.[1];
  const dependencies = [...xml.replace(/<dependencyManagement>[\s\S]*?<\/dependencyManagement>/g, '').replace(/<build>[\s\S]*?<\/build>/g, '').matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)].filter(m => !/<scope>test<\/scope>/.test(m[1])).map(m => m[1].match(/<artifactId>([^<]+)<\/artifactId>/)?.[1]);
  modules.push({directory, artifact, dependencies});
  for (const m of (xml.match(/<modules>([\s\S]*?)<\/modules>/)?.[1] || '').matchAll(/<module>([^<]+)<\/module>/g)) reactor(path.posix.join(directory, m[1], 'pom.xml'));
}
reactor('pom.xml');
modules.sort((a,b) => b.directory.length-a.directory.length);
const owner = p => modules.find(m => m.directory !== '.' && p.startsWith(m.directory+'/'))?.artifact;
const java = files.filter(p => p.includes('/src/main/java/') && p.endsWith('.java') && !p.startsWith('data-ops-framework-legacy/')).map(p => ({file:p, module:owner(p), text:read(p)}));
const classes = new Map(java.map(j => [j.text.match(/^package ([^;]+);/m)?.[1]+'.'+path.posix.basename(j.file,'.java'), j]));
const violations = [];
const baseline = JSON.parse(read('scripts/architecture/legacy-shared-persistence.json'));
for (const j of java) {
  for (const m of j.text.matchAll(/^import (?:static )?([^;]+);/gm)) {
    const name = m[1];
    if ((j.file.startsWith('data-ops-common/') || j.file.startsWith('data-ops-core/') || j.file.startsWith('data-ops-spi/') || j.file.startsWith('data-ops-plugins/')) && name.startsWith('io.yak.ops.business.')) violations.push(`${j.file}: foundation/plugin imports business ${name}`);
    if (j.module?.startsWith('data-ops-business-') && name.includes('BusinessDatabaseConfiguration')) violations.push(`${j.file}: shared persistence assembly belongs to Boot`);
    if (crossesPersistenceBoundary(j, name, classes)) violations.push(`${j.file}: cross-domain persistence ${name}`);
    if ((j.file.startsWith('data-ops-spi/') || /data-ops-plugin-[^/]+-api\/src\/main\/java\//.test(j.file)) &&
        /^(org\.springframework\.|com\.baomidou\.|org\.apache\.ibatis\.|io\.yak\.ops\.business\.)/.test(name))
      violations.push(`${j.file}: plugin contract exposes infrastructure ${name}`);
    if (name.startsWith('io.yak.ops.common.bean.po.') && !baseline.imports.includes(`${j.file}:${name}`)) violations.push(`${j.file}: new shared PO dependency ${name}; keep persistence in its owner`);
  }
}
for (const p of files.filter(p => p.startsWith('data-ops-common/src/main/java/') && p.includes('/bean/po/') && p.endsWith('.java'))) if (!baseline.files.includes(p)) violations.push(`${p}: new shared persistence type`);
const graph = new Map(modules.map(m => [m.artifact,m.dependencies.filter(d => modules.some(x=>x.artifact===d))]));
const visited = new Set();
function visit(n, trail=[]) {
  if (trail.includes(n)) { violations.push(`Maven dependency cycle: ${[...trail,n].join(' -> ')}`); return; }
  if (visited.has(n)) return;
  visited.add(n);
  for (const next of graph.get(n)||[]) visit(next,[...trail,n]);
}
for (const n of graph.keys()) visit(n);
if (violations.length) { console.error(violations.join('\n')); process.exitCode=1; }
else console.log(`Architecture boundaries passed: ${modules.length} reactor entries, ${java.length} production Java files.`);
