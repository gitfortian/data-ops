#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { analyzeFlywayOwnership } from './flyway-ownership.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const files = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], {
  cwd: root, encoding: 'utf8',
}).split('\0').filter((f) => f && fs.existsSync(path.join(root, f)));

const sources = files
  .filter((file) => file.includes('/src/main/java/') && file.endsWith('.java')
    && !file.startsWith('data-ops-framework/legacy/'))
  .map((file) => ({ file, content: fs.readFileSync(path.join(root, file), 'utf8') }))
  .filter(({ content }) => content.includes('Flyway.configure()') && /\bpublic\s+Flyway\s+/.test(content));

const audit = analyzeFlywayOwnership(sources, files);
const numberOfChains = audit.registrations.length;
if (numberOfChains < 15) {
  audit.violations.push('Flyway inventory unexpectedly small (' + numberOfChains
    + ' chains). Do not silently skip production domain migration owners.');
}
if (audit.violations.length) {
  console.error('Flyway ownership contract violations:\n' + audit.violations.join('\n'));
  process.exitCode = 1;
} else {
  console.log('Flyway ownership passed: ' + numberOfChains
    + ' owned migration chains, ' + audit.inspected + ' source files, unique history tables and local vendor scripts.');
}

if (process.argv.includes('--json')) {
  console.log(JSON.stringify({
    chains: audit.registrations,
    errors: audit.violations,
    note: 'Static explicit Flyway.configure() beans only; other migration bootstrap and DB schema constraints require runtime validation.',
  }, null, 2));
}
