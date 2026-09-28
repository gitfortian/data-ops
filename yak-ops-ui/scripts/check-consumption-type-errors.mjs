import fs from 'node:fs';

const logPath = process.argv[2];
const exitCode = Number(process.argv[3]);
if (!logPath || !Number.isInteger(exitCode) || exitCode < 0) {
  console.error('Usage: node scripts/check-consumption-type-errors.mjs <tsc-log> <tsc-exit-code>');
  process.exit(2);
}

const changedSurfaces = [
  'src/services/consumption/',
  'src/pages/data-analysis/consumption/',
  'src/pages/data-analysis/data-catalog/index.tsx',
  'src/pages/data-analysis/dataset/components/DatasetRowActions.tsx',
  'src/pages/data-asset/components/StatusFlowStrip.tsx',
  'src/pages/home/components/HomeConsumptionHub.tsx',
  'src/pages/home/HomePage.tsx',
  'config/routes.ts',
];

const diagnostics = fs
  .readFileSync(logPath, 'utf8')
  .split(/\r?\n/)
  .filter((line) => /\berror TS\d+:/.test(line));

if (exitCode !== 0 && (exitCode !== 2 || diagnostics.length === 0)) {
  console.error(`FAIL: TypeScript did not complete normally (exit ${exitCode}).`);
  process.exit(1);
}

if (exitCode === 0 && diagnostics.length > 0) {
  console.error('FAIL: TypeScript reported diagnostics but exited successfully.');
  diagnostics.forEach((line) => console.error(`  ${line}`));
  process.exit(1);
}

const relevant = diagnostics.filter((line) =>
  changedSurfaces.some((surface) => line.includes(surface)),
);

if (relevant.length > 0) {
  console.error('FAIL: TypeScript diagnostics were introduced on Phase 4 consumption surfaces:');
  relevant.forEach((line) => console.error(`  ${line}`));
  process.exit(1);
}

console.log(
  `PASS: no TypeScript diagnostics on Phase 4 consumption surfaces. ` +
    `${diagnostics.length} repository-baseline diagnostics were not attributed to this PR surface.`,
);
