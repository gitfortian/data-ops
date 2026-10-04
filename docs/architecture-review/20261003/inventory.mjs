import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const root = process.cwd();
const files = execFileSync('git', ['ls-files'], { encoding: 'utf8' }).trim().split(/\r?\n/);
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const tag = (s, name) => s.match(new RegExp(`<${name}>([^<]+)</${name}>`))?.[1];
const reactor = [];
function visit(pom) {
  const xml = read(pom);
  const direct = xml.replace(/<parent>[\s\S]*?<\/parent>/g, '');
  const artifact = tag(direct, 'artifactId');
  const directory = path.posix.dirname(pom);
  const dependencies = [...xml.replace(/<dependencyManagement>[\s\S]*?<\/dependencyManagement>/g, '').replace(/<build>[\s\S]*?<\/build>/g, '').matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)]
    .map(m => ({ artifact: tag(m[1], 'artifactId'), scope: tag(m[1], 'scope') || 'compile', optional: tag(m[1], 'optional') === 'true' }));
  reactor.push({ artifact, directory, pom, packaging: tag(direct, 'packaging') || 'jar', dependencies });
  const block = xml.match(/<modules>([\s\S]*?)<\/modules>/)?.[1] || '';
  for (const m of block.matchAll(/<module>([^<]+)<\/module>/g)) visit(path.posix.normalize(`${directory}/${m[1]}/pom.xml`));
}
visit('pom.xml');
const artifacts = new Set(reactor.map(m => m.artifact));
const sorted = [...reactor].sort((a, b) => b.directory.length - a.directory.length);
const owner = p => sorted.find(m => m.directory !== '.' && p.startsWith(m.directory + '/'))?.artifact || 'data-ops';
const production = files.filter(p => p.includes('/src/main/java/') && !p.startsWith('data-ops-framework/legacy/'));
const java = production.map(p => {
  const s = read(p);
  return { file: p, module: owner(p), lines: s.split(/\r?\n/).length, package: s.match(/^package ([^;]+);/m)?.[1], imports: [...s.matchAll(/^import (?:static )?([^;]+);/gm)].map(m => m[1]), source: s };
});
const classes = new Map(java.map(j => [`${j.package}.${path.posix.basename(j.file, '.java')}`, j]));
const crossings = [];
for (const j of java) for (const i of j.imports) {
  let name = i;
  while (name.includes('.') && !classes.has(name)) name = name.slice(0, name.lastIndexOf('.'));
  const target = classes.get(name);
  if (target && target.module !== j.module) crossings.push({ from: j.module, to: target.module, file: j.file, imported: i });
}
const internalBusinessImports = crossings.filter(e => e.from.startsWith('data-ops-business-') && e.to.startsWith('data-ops-business-') && !/\.(api|spi)(\.|$)/.test(e.imported));
const modules = reactor.map(m => ({ ...m, dependencies: m.dependencies.filter(d => artifacts.has(d.artifact)), javaFiles: java.filter(j => j.module === m.artifact).length, javaLines: java.filter(j => j.module === m.artifact).reduce((s, j) => s + j.lines, 0), architectureTests: files.filter(p => owner(p) === m.artifact && p.includes('/src/test/') && /Architecture|Boundary|Dependency|Contract/.test(p)).length }));
const graph = new Map(modules.map(m => [m.artifact, m.dependencies.filter(d => d.scope !== 'test').map(d => d.artifact)]));
const cycles = [];
const visited = new Set(), stack = [];
function walk(n) {
  if (stack.includes(n)) { cycles.push([...stack.slice(stack.indexOf(n)), n]); return; }
  if (visited.has(n)) return;
  stack.push(n); for (const next of graph.get(n) || []) walk(next); stack.pop(); visited.add(n);
}
for (const n of graph.keys()) walk(n);
const sourceUi = files.filter(p => p.startsWith('data-ops-ui/src/') && /\.(ts|tsx)$/.test(p) && !/\.test\./.test(p));
const uiSizes = sourceUi.map(p => ({ file: p, lines: read(p).split(/\r?\n/).length }));
const result = { commit: execFileSync('git', ['rev-parse', 'HEAD'], {encoding:'utf8'}).trim(), method: 'Tracked files only; declared reactor dependencies, not effective Maven POM; static imports, not runtime wiring; line counts are triage signals.', reactorModules: reactor.length, productionJavaFiles: java.length, productionJavaLines: java.reduce((s,j)=>s+j.lines,0), uiSourceFiles: sourceUi.length, uiSourceLines: uiSizes.reduce((s,j)=>s+j.lines,0), compileCycles: cycles, modules, largestJava: [...java].sort((a,b)=>b.lines-a.lines).slice(0,25).map(({source,imports,...j})=>j), largestUi: uiSizes.sort((a,b)=>b.lines-a.lines).slice(0,25), crossModuleImports: crossings.length, businessImportsOutsideApi: internalBusinessImports };
fs.writeFileSync('docs/architecture-review/20261003/inventory.json', JSON.stringify(result, null, 2));
console.log(JSON.stringify({ commit: result.commit, reactorModules: result.reactorModules, productionJavaFiles: result.productionJavaFiles, uiSourceFiles: result.uiSourceFiles, compileCycles: result.compileCycles, crossModuleImports: result.crossModuleImports }, null, 2));
