import { checkServiceTransportImports } from './frontend-service-transport.mjs';
import { execFileSync } from 'node:child_process';
import { readFileSync, existsSync, writeFileSync } from 'node:fs';
import path from 'node:path';
const files = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], { encoding: 'utf8' })
  .split('\0').filter(file => file.startsWith('data-ops-ui/src/') && /\.tsx?$/.test(file) && !/\.test\.tsx?$/.test(file) && existsSync(file));
const edges = [];
const serviceTransportViolations = [];
for (const file of files) {
  const source = readFileSync(file, 'utf8');
  serviceTransportViolations.push(...checkServiceTransportImports(file, source));
  const module = file.match(/^data-ops-ui\/src\/pages\/([^/]+)/)?.[1];
  for (const match of source.matchAll(/(?:from\s*|import\s*)['"]([^'"]+)['"]/g)) {
    const imported = match[1];
    const target = imported.startsWith('@/') ? 'data-ops-ui/src/' + imported.slice(2) :
      imported.startsWith('.') ? path.posix.normalize(path.posix.join(path.posix.dirname(file), imported)) : undefined;
    const targetModule = target?.match(/^data-ops-ui\/src\/pages\/([^/]+)/)?.[1];
    if (targetModule && targetModule !== module) edges.push(`${file}:${imported}`);
  }
}
const baselinePath = 'scripts/architecture/frontend-corridors.json';
if (process.argv.includes('--record')) writeFileSync(baselinePath, JSON.stringify([...new Set(edges)].sort(), null, 2) + '\n');
const allowed = new Set(JSON.parse(readFileSync(baselinePath, 'utf8')));
const added = edges.filter(edge => !allowed.has(edge));
if (added.length) throw new Error('New cross-page coupling; move the shared contract to its owner:\n' + added.join('\n'));
if (serviceTransportViolations.length)
  throw new Error('Domain Services must use the shared HTTP client, not raw Umi request:\n' + serviceTransportViolations.join('\n'));
console.log(`Frontend boundaries passed (${edges.length} declared legacy corridors).`);
