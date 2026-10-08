#!/usr/bin/env node
/**
 * A8.1c: Preserve the verified legacy Common and Schedule corridor without
 * widening it, until the Security Platform + Schedule owner migration.
 * Source-only architecture guard, not evidence of zero effective dependencies.
 */
import { execFileSync } from 'node:child_process';
import { readFileSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
export const ENUM_NAMES = Object.freeze([
  'alert', 'approval', 'asset', 'datasource', 'lifecycle', 'mdm',
  'metadata', 'metric', 'modeling', 'resource', 'security', 'semantic',
]);
const ROOT_COMMON = 'data-ops-common/src/main/java/';
const ROOT_SECURITY = 'data-ops-framework/data-security/src/main/java/';
const SCHEDULE_GATEWAY = ROOT_COMMON + 'io/yak/ops/common/schedule/YakScheduleGateway.java';
const legacyReference = /\bio\.yak\.framework\.(?:common|schedule|file|security)(?:\.[a-zA-Z_$][\w$]*|\.\*)+/g;
const securityToProduct = /\bio\.yak\.ops\.(?:common|business|boot)(?:\.[a-zA-Z_$][\w$]*|\.\*)+/g;
const allowed = new Map([
  ...ENUM_NAMES.map(n => [ROOT_COMMON + 'io/yak/ops/common/enums/' + n + '/'
    + (n === 'datasource' ? 'DataSource' : n.charAt(0).toUpperCase() + n.slice(1)) + 'ErrorCode.java',
    new Set(['io.yak.framework.common.ErrorCode'])]),
  [SCHEDULE_GATEWAY, new Set([
    'io.yak.framework.schedule.api.ScheduleDefinition',
    'io.yak.framework.schedule.api.ScheduleKey',
    'io.yak.framework.schedule.api.ScheduleManager',
    'io.yak.framework.schedule.api.ScheduleSnapshot',
  ])],
]);

export function findViolations(files) {
  const failures = [];
  for (const [path, source] of files) {
    if (path.startsWith(ROOT_COMMON) && path.endsWith('.java')) {
      const permitted = allowed.get(path) || new Set();
      for (const ref of source.match(legacyReference) || []) {
        if (!permitted.has(ref)) {
          failures.push(path + ': unapproved product Common -> Framework reference ' + ref);
        }
      }
    }
    if (path.startsWith(ROOT_SECURITY) && path.endsWith('.java')) {
      for (const ref of source.match(securityToProduct) || []) {
        failures.push(path + ': Framework Security cannot reverse-depend on product code ' + ref);
      }
    }
    if (path === 'data-ops-framework/data-security/pom.xml') {
      const xml = source.replace(/<!--[\s\S]*?-->/g, '');
      for (const block of xml.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)) {
        if (/<groupId>\s*io\.yak\.ops\s*<\/groupId>/.test(block[1])
          && /<artifactId>\s*data-ops-(?:common|business-[^<]+|boot)\s*<\/artifactId>/.test(block[1])) {
          failures.push(path + ': Framework Security must not depend on product Common/Business/Boot Maven artifacts');
        }
      }
    }
  }
  return failures.sort();
}

export function scanRepository(root = ROOT) {
  const raw = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], {
    cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024,
  });
  const out = new Map();
  for (const path of raw.split('\0')) {
    if (!(path === 'data-ops-framework/data-security/pom.xml'
        || ((path.startsWith(ROOT_COMMON) || path.startsWith(ROOT_SECURITY))
          && path.endsWith('.java')))) continue;
    const abs = resolve(root, path);
    if (existsSync(abs)) out.set(path, readFileSync(abs, 'utf8'));
  }
  if (!out.has('data-ops-framework/data-security/pom.xml')) {
    throw new Error('Security POM missing; fail closed until A8.2 changes the owner explicitly');
  }
  if (![...out.keys()].some(p => p.startsWith(ROOT_COMMON))) {
    throw new Error('Product Common Java source missing');
  }
  return out;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const violations = findViolations(scanRepository());
    if (violations.length) {
      console.error(violations.join('\n'));
      process.exitCode = 1;
    } else {
      console.log('A8.1c Common/Security corridor guard passed: grandfathered ErrorCode/Schedule imports only.');
    }
  } catch (error) {
    console.error('A8.1c corridor FAILED: ' + error.message);
    process.exitCode = 1;
  }
}
