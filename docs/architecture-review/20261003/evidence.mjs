import fs from 'node:fs';
import { execFileSync } from 'node:child_process';

const files = execFileSync('git', ['ls-files'], { encoding: 'utf8' }).trim().split(/\r?\n/);
const read = p => fs.readFileSync(p, 'utf8');
const inventory = JSON.parse(read('docs/architecture-review/20261003/inventory.json'));
const commonPo = files.filter(p => p.startsWith('data-ops-common/src/main/java/') && p.includes('/bean/po/') && p.endsWith('.java'));
const unsafeCrossings = inventory.businessImportsOutsideApi.filter(e => /\.(dao|mapper)(\.|$)|\.repository\.impl(\.|$)/.test(e.imported));
const businessTestCounts = inventory.modules.filter(m => m.artifact.startsWith('data-ops-business-') && m.javaFiles > 0).map(m => ({ module: m.artifact, javaFiles: m.javaFiles, testFiles: files.filter(p => p.startsWith(m.directory + '/src/test/java/') && p.endsWith('.java')).length, guardNamedFiles: m.architectureTests }));
const ui = files.filter(p => p.startsWith('data-ops-ui/src/') && /\.(ts|tsx)$/.test(p) && !/\.test\./.test(p));
const pageImports = [];
const requestClients = {};
for (const p of ui) {
  const s = read(p);
  for (const m of s.matchAll(/(?:from\s*|import\s*\()['"](@\/pages\/[^'"]+)/g)) pageImports.push({ file: p, imported: m[1] });
  for (const name of ['@/utils/request', '@umijs/max', 'umi-request']) {
    if (new RegExp('(?:from\\s*|import\\s*\\()[\'\"]' + name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\'\"]').test(s) && /\brequest\b/.test(s)) requestClients[name] = (requestClients[name] || 0) + 1;
  }
}
const largestUiHooks = inventory.largestUi.slice(0, 12).map(e => ({ ...e, useStateCalls: [...read(e.file).matchAll(/\buseState\s*(?:<[^;\n]*?>)?\s*\(/g)].length, useEffectCalls: [...read(e.file).matchAll(/\buseEffect\s*\(/g)].length }));
const flywayMissingLiterals = [];
for (const p of files.filter(p => p.endsWith('FlywayContractTest.java'))) {
  const module = inventory.modules.filter(m => p.startsWith(m.directory + '/')).sort((a,b) => b.directory.length - a.directory.length)[0];
  if (!module) continue;
  const expected = [...new Set([...read(p).matchAll(/"([VB]\d+__[^"]+\.sql)"/g)].map(m => m[1]))];
  const present = files.filter(f => f.startsWith(module.directory + '/src/main/resources/') && f.endsWith('.sql')).map(f => f.split('/').pop());
  const missing = expected.filter(name => !present.includes(name));
  if (missing.length) flywayMissingLiterals.push({ test: p, missing, present });
}
const result = { method: 'Tracked static source only. Request-client counts are import heuristics; page imports are not all cross-domain; named guards are not equivalent to behavioral coverage. Missing migration literals are compared with owning-module resources; tests have not been executed.', commonPersistenceObjects: commonPo.length, crossBusinessPersistenceImports: unsafeCrossings, businessTestCounts, requestClientCandidateCounts: requestClients, pageImports, largestUiHooks, flywayMissingLiterals };
fs.writeFileSync('docs/architecture-review/20261003/evidence.json', JSON.stringify(result, null, 2));
console.log(JSON.stringify({ commonPersistenceObjects: result.commonPersistenceObjects, crossBusinessPersistenceImports: result.crossBusinessPersistenceImports, flywayMissingLiterals }, null, 2));
