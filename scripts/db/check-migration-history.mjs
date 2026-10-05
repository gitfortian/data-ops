import { readFileSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const history = JSON.parse(readFileSync('scripts/db/migration-baselines.json', 'utf8'));
const failures = [];
const migrations = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '--',
  '*src/main/resources*db/migration*/*.sql'], { encoding: 'utf8' }).trim().split(/\r?\n/).filter(Boolean);
for (const file of migrations) {
  const counterpart = file.includes('/migration-postgresql/')
    ? file.replace('/migration-postgresql/', '/migration/')
    : file.replace('/migration/', '/migration-postgresql/');
  if (!existsSync(counterpart)) failures.push(`Storage vendor migration missing: ${counterpart}`);
}
for (const [file, expected] of Object.entries(history)) {
  if (!existsSync(file)) { failures.push(`Applied baseline removed: ${file}`); continue; }
  const digest = createHash('sha256').update(readFileSync(file, 'utf8').replaceAll('\r\n', '\n')).digest('hex');
  if (digest !== expected) failures.push(`Applied baseline changed: ${file}; add a forward migration.`);
}
if (failures.length) { console.error(failures.join('\n')); process.exitCode = 1; }
else console.log(`Migration history passed: ${Object.keys(history).length} pinned baseline scripts.`);
