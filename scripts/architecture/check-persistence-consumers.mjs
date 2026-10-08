#!/usr/bin/env node
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { persistenceInventory } from './persistence-consumers.mjs';

const root = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const files = execFileSync('git', ['ls-files', '-z', '--', '*.java'], {
  cwd: root, encoding: 'utf8',
}).split('\0').filter(Boolean);
const sources = files.filter((file) => file.includes('/src/main/java/')
  && !file.startsWith('data-ops-framework/legacy/'))
  .filter((file) => existsSync(resolve(root, file)))
  .map((file) => ({ file, content: readFileSync(resolve(root, file), 'utf8') }));

const inventory = persistenceInventory(sources);
if (process.argv.includes('--json')) console.log(JSON.stringify(inventory, null, 2));
else {
  console.log('A2.2 Boot persistence bean consumer inventory');
  for (const row of inventory.modules) {
    console.log('  ' + row.module + ': ' + row.aliases.join(', '));
  }
  console.log('References: ' + inventory.consumers.length + ', modules: '
    + inventory.modules.length + ', production Java sources: ' + sources.length);
}

if (inventory.declared.missing.length || inventory.declared.duplicate.length) {
  for (const value of inventory.declared.missing)
    console.error('Missing Boot @Bean alias: ' + value.kind + ' / ' + value.bean);
  for (const value of inventory.declared.duplicate)
    console.error('Duplicate Boot @Bean alias: ' + value.kind + ' / ' + value.bean);
  process.exitCode = 1;
}
if (sources.length < 100) {
  console.error('Production Java inventory too small: ' + sources.length);
  process.exitCode = 1;
}
