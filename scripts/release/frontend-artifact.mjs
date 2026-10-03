import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { dirname, resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

export function verifyFrontendArtifact(root = resolve(dirname(fileURLToPath(import.meta.url)), '../..'), write = false) {
const dist = resolve(root, 'data-ops-ui/dist');
const manifestPath = resolve(dist, 'build-manifest.json');
const git = args => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim();
function digest(files, base, normalizeText = false) {
  const hash = createHash('sha256');
  for (const name of files.map(name => name.replaceAll('\\', '/')).sort()) {
    const contents = readFileSync(resolve(base, name));
    hash.update(name.replaceAll('\\', '/')).update('\0')
      .update(normalizeText ? contents.toString('utf8').replaceAll('\r\n', '\n') : contents).update('\0');
  }
  return hash.digest('hex');
}
function assets(directory) {
  return readdirSync(directory).flatMap(name => {
    const entry = resolve(directory, name);
    return statSync(entry).isDirectory() ? assets(entry) : [relative(dist, entry)];
  }).filter(name => name !== 'build-manifest.json');
}
if (!existsSync(resolve(dist, 'index.html'))) throw new Error('Build the frontend before distribution packaging.');
const sources = git(['ls-files', '-z']).split('\0').filter(name => name && existsSync(resolve(root, name)) &&
  /^(data-ops-|scripts\/|pom\.xml)/.test(name) && /\.(java|sql|xml|tsx?|css|less|mjs|json|ya?ml|lock)$/.test(name));
const pom = readFileSync(resolve(root, 'pom.xml'), 'utf8').replace(/<parent>[\s\S]*?<\/parent>/g, '');
const current = {
  sourceRevision: git(['rev-parse', 'HEAD']),
  sourceDigest: digest(sources, root, true),
  assetDigest: digest(assets(dist), dist),
  version: pom.match(/<version>([^<]+)<\/version>/)?.[1],
  javaRelease: 21,
  schemaPolicy: 'current-baseline-and-forward-migrations',
};
if (write) {
  writeFileSync(manifestPath, JSON.stringify(current, null, 2) + '\n');
  console.log('Frontend build manifest written.');
} else {
  if (!existsSync(manifestPath)) throw new Error('Frontend build manifest is missing; run yarn build.');
  const previous = JSON.parse(readFileSync(manifestPath, 'utf8'));
  for (const [key, value] of Object.entries(current)) {
    if (previous[key] !== value) throw new Error(`Frontend artifact mismatch: ${key}; rebuild from the current source.`);
  }
  console.log('Frontend revision, source and artifact checksums verified.');
}

}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url))
  verifyFrontendArtifact(undefined, process.argv.includes('--write'));
