#!/usr/bin/env node
/**
 * A8.1a: retire the unconsumed legacy data-file artifact from the application
 * reactor and dependency graph, without deleting its separately publishable
 * historical source or changing Resource/Storage business behavior.
 *
 * This is a repository-scoped structural gate, not an external Maven Central
 * consumer audit.
 */
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const FILE_DIR = 'data-ops-framework/data-file/';
const LEGACY_DIR = 'data-ops-framework/legacy/';
const FRAMEWORK_POM = 'data-ops-framework/pom.xml';
const REMOVED_ARTIFACT = 'data-file';

function noComments(xml) {
  return xml.replace(/<!--[\s\S]*?-->/g, '');
}

export function issues(files) {
  const findings = [];
  const frameworkPom = files.get(FRAMEWORK_POM);
  if (!frameworkPom) {
    findings.push(FRAMEWORK_POM + ': unable to verify Framework reactor');
  } else if (/<module>\s*data-file\s*<\/module>/.test(noComments(frameworkPom))) {
    findings.push(FRAMEWORK_POM + ': retired data-file must not be a reactor module');
  }

  for (const [path, source] of files) {
    // A8.5 owns historical/external compatibility. The old library itself
    // remains buildable as a standalone project during the transition.
    if (path.startsWith(FILE_DIR) || path.startsWith(LEGACY_DIR)) continue;
    if (path === 'pom.xml' || path.endsWith('/pom.xml')) {
      const xml = noComments(source);
      const blocks = [...xml.matchAll(/<(dependency|plugin)>([\s\S]*?)<\/\1>/g)];
      for (const [,kind,block] of blocks) {
        if (/<artifactId>\s*data-file\s*<\/artifactId>/.test(block)) {
          findings.push(path + ': retired data-file ' + kind + ' dependency/management forbidden');
        }
      }
    }
    if (/\/src\/main\/java\/.*\.java$/.test(path)
        || /\/src\/main\/resources\/.*\.(?:imports|xml|json|yaml|yml|properties|sql)$/.test(path)) {
      if (/\bio\.yak\.framework\.file(?:\.|\b)/.test(source)) {
        findings.push(path + ': old Framework File namespace is not a production dependency');
      }
    }
  }
  return findings.sort();
}

export function filesInRepository(root = ROOT) {
  const files = new Map();
  const output = execFileSync('git',
    ['ls-files', '--cached', '--others', '--exclude-standard', '-z'],
    { cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
  for (const path of output.split('\0')) {
    if (!path || path.startsWith(FILE_DIR) || path.startsWith(LEGACY_DIR)) continue;
    if (path !== 'pom.xml' && !path.endsWith('/pom.xml')
        && !/\/src\/main\/java\/.*\.java$/.test(path)
        && !/\/src\/main\/resources\/.*\.(?:imports|xml|json|yaml|yml|properties|sql)$/.test(path)) continue;
    const disk = resolve(root, path);
    if (existsSync(disk)) files.set(path, readFileSync(disk, 'utf8'));
  }
  return files;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const problems = issues(filesInRepository());
    if (problems.length) {
      for (const problem of problems) console.error(problem);
      process.exitCode = 1;
    } else {
      console.log('A8.1 File retirement passed: no active reactor module, POM consumer, or Java/resource File reference.');
    }
  } catch (error) {
    console.error('A8.1 File retirement FAILED: ' + error.message);
    process.exitCode = 1;
  }
}
