// Apply the audit's minimum text sizes to explicitly selected non-AI modules.
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../../../../data-ops-ui/src/pages');
const modules = process.argv.slice(2);
const changed = [];
function visit(folder) {
  for (const entry of fs.readdirSync(folder, { withFileTypes: true })) {
    const filename = path.join(folder, entry.name);
    if (entry.isDirectory()) visit(filename);
    else if (entry.name.endsWith('.tsx') && !entry.name.includes('.test.')) {
      const source = fs.readFileSync(filename, 'utf8');
      const updated = source.replace(/text-\[1[01]px\]/g, 'text-[12px]')
        .replace(/text-\[#(?:98a2b3|98A2B3|a0a5ad|B0B7C3)\]/g, 'text-[#667085]');
      if (source !== updated) {
        fs.writeFileSync(filename, updated);
        changed.push(path.relative(root, filename));
      }
    }
  }
}
for (const name of modules) {
  if (!/^[a-z-]+$/.test(name) || name === 'ai-agent') throw new Error('Invalid module');
  visit(path.join(root, name));
}
console.log(JSON.stringify({ modules, changed }, null, 2));
