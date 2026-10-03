// Compare this branch's diagnostics with HEAD without rewriting the checkout.
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const repo = path.resolve(__dirname, '../../../');
const ui = path.join(repo, 'data-ops-ui');
const ts = require(path.join(ui, 'node_modules/typescript'));
const config = ts.readConfigFile(path.join(ui, 'tsconfig.json'), ts.sys.readFile);
const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, ui, undefined, path.join(ui, 'tsconfig.json'));
const changed = execFileSync('git', ['diff', '--name-only', '--', 'data-ops-ui'], { cwd: repo, encoding: 'utf8' })
  .trim().split(/\r?\n/).filter(p => /\.[jt]sx?$/.test(p));
const original = new Map(changed.map(p => [path.resolve(repo, p).toLowerCase(),
  execFileSync('git', ['show', `HEAD:${p}`], { cwd: repo, encoding: 'utf8' })]));
const added = new Set(execFileSync('git', ['ls-files', '--others', '--exclude-standard', '--', 'data-ops-ui/src'], { cwd: repo, encoding: 'utf8' })
  .trim().split(/\r?\n/).filter(p => /\.[jt]sx?$/.test(p)));
function diagnostics(baseline) {
  const host = ts.createCompilerHost(parsed.options);
  const read = host.readFile;
  if (baseline) host.readFile = filename => original.get(path.resolve(filename).toLowerCase()) ?? read(filename);
  const program = ts.createProgram(parsed.fileNames, { ...parsed.options, noEmit: true }, host);
  return ts.getPreEmitDiagnostics(program).map(d => ({
    file: d.file ? path.relative(repo, d.file.fileName).replaceAll('\\', '/') : '',
    code: d.code, message: ts.flattenDiagnosticMessageText(d.messageText, '\n'),
  }));
}
const baseline = diagnostics(true);
console.log(`HEAD diagnostics: ${baseline.length}`);
const current = diagnostics(false);
const key = d => `${d.file}:${d.code}:${d.message}`;
const counts = new Map();
for (const d of baseline) counts.set(key(d), (counts.get(key(d)) ?? 0) + 1);
const introduced = current.filter(d => {
  if (added.has(d.file)) return true;
  const count = counts.get(key(d)) ?? 0;
  if (count > 0) { counts.set(key(d), count - 1); return false; }
  return true;
});
const result = { baselineCount: baseline.length, currentCount: current.length, introduced };
fs.writeFileSync(path.join(__dirname, 'typecheck-comparison.json'), JSON.stringify(result, null, 2));
console.log(JSON.stringify(result, null, 2));
process.exitCode = introduced.length ? 1 : 0;
