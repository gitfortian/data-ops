from pathlib import Path
p=Path('scripts/release/frontend-artifact.mjs');s=p.read_text(encoding='utf-8');s=s.replace('const root = resolve(dirname(fileURLToPath(import.meta.url)), \'../..\');','''export function verifyFrontendArtifact(root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), write = false) {''');s=s.replace('for (const name of [...files].sort()) {','for (const name of files.map(name => name.replaceAll(\'\\\\\', \'/\')).sort()) {');s=s.replace("if (process.argv.includes('--write')) {",'if (write) {');s += '''\n}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url))
  verifyFrontendArtifact(undefined, process.argv.includes('--write'));
''';p.write_text(s,encoding='utf-8')
p=Path('scripts/release/frontend-artifact.test.mjs');p.write_text('''import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname, basename, resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { verifyFrontendArtifact } from './frontend-artifact.mjs';

test('packaging rejects missing UI, stale revision, changed source and modified assets', () => {
  const root = mkdtempSync(join(tmpdir(), 'data-ops-artifact-test-'));
  const git = args => execFileSync('git', ['-C', root, ...args], { stdio: 'pipe' });
  try {
    mkdirSync(join(root, 'data-ops-ui/dist'), { recursive: true });
    writeFileSync(join(root, 'pom.xml'), '<project><version>1.0.0</version></project>');
    writeFileSync(join(root, 'data-ops-ui/source.ts'), 'export const version = 1;');
    writeFileSync(join(root, 'data-ops-ui/dist/index.html'), '<html>v1</html>');
    git(['init']); git(['add', 'pom.xml', 'data-ops-ui/source.ts']);
    git(['-c', 'user.name=Artifact Test', '-c', 'user.email=artifact-test@example.invalid', 'commit', '-m', 'fixture']);
    assert.throws(() => verifyFrontendArtifact(root), /manifest is missing/);
    verifyFrontendArtifact(root, true); verifyFrontendArtifact(root);
    writeFileSync(join(root, 'data-ops-ui/dist/index.html'), '<html>tampered</html>');
    assert.throws(() => verifyFrontendArtifact(root), /assetDigest/);
    verifyFrontendArtifact(root, true);
    writeFileSync(join(root, 'data-ops-ui/source.ts'), 'export const version = 2;');
    assert.throws(() => verifyFrontendArtifact(root), /sourceDigest/);
    verifyFrontendArtifact(root, true);
    git(['add', 'data-ops-ui/source.ts']);
    git(['-c', 'user.name=Artifact Test', '-c', 'user.email=artifact-test@example.invalid', 'commit', '-m', 'revision']);
    assert.throws(() => verifyFrontendArtifact(root), /sourceRevision/);
    rmSync(join(root, 'data-ops-ui/dist/index.html'));
    assert.throws(() => verifyFrontendArtifact(root), /Build the frontend/);
  } finally {
    if (dirname(resolve(root)) !== resolve(tmpdir()) || !basename(root).startsWith('data-ops-artifact-test-'))
      throw new Error('Unexpected cleanup target');
    rmSync(root, { recursive: true, force: true });
  }
});
''',encoding='utf-8')
p=Path('.github/workflows/architecture-checks.yml');s=p.read_text(encoding='utf-8').replace('      - run: node --test scripts/architecture/*.test.mjs','      - run: node --test scripts/architecture/*.test.mjs scripts/release/*.test.mjs');p.write_text(s,encoding='utf-8')
