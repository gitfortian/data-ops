#!/usr/bin/env node
/**
 * A8.0 framework-coupling inventory / no-new-legacy-dependency guard.
 *
 * Compares production Framework reference *identities and occurrence counts*
 * with an immutable pre-A8 git commit. Existing debt may go away; a new
 * reference in a different source file, a new symbol or an added duplicate
 * may not silently re-enter. The snapshot is NOT a license to keep debt.
 *
 * No network, Maven resolution, credentials, package installation or writes.
 */
import { execFileSync, spawnSync } from 'node:child_process';
import { readFileSync, existsSync } from 'node:fs';
import { resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

export const A8_BASELINE_SHA = '3919d93962fafe0e9394be5b3735220fb64bfa0c';
const HERE = resolve(fileURLToPath(import.meta.url), '..');
const REPO_ROOT = resolve(HERE, '../..');
const FRAMEWORK_DIR = 'data-ops-framework/';
const FRAMEWORK_GROUP = /^(?:io\.github\.weifuwan|io\.yak\.framework)$/;
const FRAMEWORK_ARTIFACT = /^(?:data-ops-framework-parent|data-common|data-file|data-security(?:-spring-boot-starter)?|data-schedule(?:-[a-z0-9-]+)?|data-workflow(?:-[a-z0-9-]+)?)$/;
const JAVA_REF = /\bio\.yak\.framework(?:\.[A-Za-z_$][\w$]*|\.\*)+/g;
const ASSET_REF = /\bio\.yak\.framework(?:\.[A-Za-z_$][\w$]*|\.\*)+/g;

function git(root, args, allowNoMatch = false) {
  const result = spawnSync('git', args, { cwd: root, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 });
  if (result.error || (result.status !== 0 && !(allowNoMatch && result.status === 1))) {
    throw new Error('git ' + args.slice(0, 2).join(' ') + ' failed: '
      + (result.error?.message || result.stderr || 'exit ' + result.status));
  }
  return result.stdout;
}

export function eligible(path) {
  if (path.startsWith(FRAMEWORK_DIR + 'legacy/')) return false;
  if (path === 'pom.xml' || path.endsWith('/pom.xml')) return 'pom';
  if (/\/src\/main\/java\/.*\.java$/.test(path)) return 'java';
  if (/\/src\/main\/resources\/.*\.(?:imports|xml|json|yaml|yml|properties|sql)$/.test(path)) return 'resource';
  if (/^(?:\.github\/workflows\/.*\.ya?ml|Dockerfile|compose[^/]*\.ya?ml)$/.test(path)) return 'assembly';
  return null;
}

function withoutXmlComments(text) {
  return text.replace(/<!--[\s\S]*?-->/g, '');
}

/** Strictly inspect group + artifact together, including parent, managed and direct dependency. */
export function scanPom(text) {
  const xml = withoutXmlComments(text);
  const hits = [];
  const blocks = [...xml.matchAll(/<(parent|dependency|plugin)>([\s\S]*?)<\/\1>/g)];
  // The own group/artifact pair of an old Framework module is a package
  // declaration, rather than a consumer dependency. Keep it separately visible.
  const header = xml.replace(/<parent>[\s\S]*?<\/parent>/g, '')
    .replace(/<dependencies>[\s\S]*?<\/dependencies>/g, '')
    .replace(/<dependencyManagement>[\s\S]*?<\/dependencyManagement>/g, '')
    .replace(/<build>[\s\S]*?<\/build>/g, '')
    .replace(/<modules>[\s\S]*?<\/modules>/g, '');
  blocks.push(['', 'declaration', header]);
  for (const block of blocks) {
    const role = block[1];
    const fragment = block[2];
    const group = fragment.match(/<groupId>\s*([^<\s]+)\s*<\/groupId>/)?.[1];
    const artifact = fragment.match(/<artifactId>\s*([^<\s]+)\s*<\/artifactId>/)?.[1];
    if (FRAMEWORK_GROUP.test(group || '') && FRAMEWORK_ARTIFACT.test(artifact || '')) {
      hits.push('maven:' + role + ':' + group + ':' + artifact);
    }
  }
  return hits;
}

export function references(path, text) {
  const kind = eligible(path);
  if (!kind) return [];
  if (kind === 'pom') return scanPom(text);
  const pattern = kind === 'java' ? JAVA_REF : ASSET_REF;
  return [...text.matchAll(pattern)].map(hit => 'symbol:' + hit[0]);
}

function counted(records) {
  const counts = new Map();
  for (const [path, text] of records) {
    for (const ref of references(path, text)) {
      const key = path + '\t' + ref;
      counts.set(key, (counts.get(key) || 0) + 1);
    }
  }
  return counts;
}

/**
 * Worktree scan includes git-tracked and untracked nonignored files.
 * A commit scan selects candidate files with git grep, avoiding git-show on
 * ~10k unrelated source files in the pinned snapshot.
 */
export function collect(root, revision = null) {
  if (revision) {
    git(root, ['rev-parse', '--verify', revision + '^{commit}']);
    const output = git(root, ['grep', '-I', '-l', '-F',
      '-e', 'io.yak.framework', '-e', 'io.github.weifuwan', revision, '--'], true);
    const paths = output.split('\n').filter(Boolean).map(line => {
      const prefix = revision + ':';
      if (!line.startsWith(prefix)) throw new Error('unexpected git grep entry: ' + line);
      return line.slice(prefix.length);
    }).filter(eligible);
    return counted(paths.map(p => [p, git(root, ['show', revision + ':' + p])]));
  }
  const paths = git(root, ['ls-files', '--cached', '--others', '--exclude-standard', '-z'])
    .split('\0').filter(p => p && eligible(p) && existsSync(resolve(root, p)));
  return counted(paths.map(p => [p, readFileSync(resolve(root, p), 'utf8')]));
}

export function compare(baseline, current) {
  return [...current.entries()].filter(([key, value]) => value > (baseline.get(key) || 0))
    .map(([key, value]) => ({
      path: key.split('\t')[0],
      reference: key.split('\t')[1],
      old: baseline.get(key) || 0,
      current: value,
    })).sort((a, b) => a.path.localeCompare(b.path) || a.reference.localeCompare(b.reference));
}

export function report(root, baselineSha = A8_BASELINE_SHA) {
  const old = collect(root, baselineSha);
  const current = collect(root);
  const summarize = counts => {
    const byFamily = {};
    const files = new Set();
    let references = 0;
    for (const [key, count] of counts) {
      const path = key.split('\t')[0];
      const family = path.startsWith(FRAMEWORK_DIR)
        ? 'framework-source' : path.split('/')[0];
      byFamily[family] = (byFamily[family] || 0) + count;
      files.add(path);
      references += count;
    }
    return { files: files.size, distinctReferences: counts.size, referenceOccurrences: references, byFamily };
  };
  const outstanding = [...current.entries()].map(([key, count]) => {
    const [path, reference] = key.split('\t');
    return { path, reference, count, baselineCount: old.get(key) || 0 };
  }).sort((a, b) => a.path.localeCompare(b.path) || a.reference.localeCompare(b.reference));
  return {
    kind: 'a8-framework-coupling',
    baselineCommit: baselineSha,
    description: 'Source-level inventory; not a resolved Maven dependency graph or runtime proof',
    baseline: summarize(old),
    current: summarize(current),
    newlyIntroduced: compare(old, current),
    outstanding,
    generatedAt: new Date().toISOString(),
  };
}

function main() {
  const mode = process.argv[2] || '--check';
  if (!['--check', '--report'].includes(mode) || process.argv.length > 3) {
    throw new Error('usage: node scripts/architecture/framework-coupling.mjs [--check|--report]');
  }
  // Never allow CLI rebaselining around the immutable A8 pre-migration anchor.
  const result = report(REPO_ROOT);
  if (mode === '--report') {
    process.stdout.write(JSON.stringify(result, null, 2) + '\n');
    return;
  }
  if (result.newlyIntroduced.length) {
    for (const ref of result.newlyIntroduced) {
      console.error(ref.path + ': new Framework coupling '
        + ref.reference + ' (baseline=' + ref.old + ', current=' + ref.current + ')');
    }
    process.exitCode = 1;
    return;
  }
  console.log('A8 Framework coupling guard passed; '
    + result.current.referenceOccurrences + ' grandfathered source references across '
    + result.current.files + ' files; baseline=' + A8_BASELINE_SHA.slice(0, 12)
    + '. See --report for the exact debt ledger.');
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main(); } catch (error) {
    console.error('A8 framework coupling guard FAILED: ' + error.message);
    process.exitCode = 1;
  }
}
