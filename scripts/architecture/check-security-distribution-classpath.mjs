#!/usr/bin/env node
/**
 * A8.2 release acceptance: scan the ACTUAL executable Boot JAR and packaged
 * distribution for moved Security binary types, rather than source locations.
 * This is a release-classpath/unique-owner check, not an old-JAR ABI proof.
 */
import { spawnSync } from 'node:child_process';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CONTRACT_TYPES } from './check-security-api-contract-owner.mjs';
import { OWNED } from './check-security-persistence-owner.mjs';
import { PURE_SPI, RUNTIME_IMPLEMENTATIONS } from './check-security-extension-owner.mjs';
import { TYPES } from './check-security-identity-context-owner.mjs';
import { SUPPORT_CLASSES } from './check-security-runtime-owner.mjs';
import { NAMES } from './check-security-declaration-owner.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
export const ARTIFACTS = Object.freeze({
  contract: 'data-ops-platform-security-contract',
  runtime: 'data-ops-platform-security-runtime',
  persistence: 'data-ops-platform-security-persistence',
  starter: 'data-security-spring-boot-starter',
});

export function expectedBinaryOwners() {
  const expected = new Map();
  function add(owner, files) {
    for (const file of files) {
      if (!file.endsWith('.java')) throw Error('Invalid Security source manifest: ' + file);
      const className = 'io/yak/framework/security/' + file.slice(0, -5) + '.class';
      if (expected.has(className) && expected.get(className) !== owner) {
        throw Error('Conflicting Security binary manifest owner: ' + className);
      }
      expected.set(className, owner);
    }
  }
  add('contract', CONTRACT_TYPES);
  add('persistence', OWNED);
  add('contract', PURE_SPI);
  add('runtime', RUNTIME_IMPLEMENTATIONS);
  add('contract', TYPES);
  add('contract', NAMES.map(x => 'permission/' + x + '.java'));
  add('runtime', SUPPORT_CLASSES);
  add('runtime', ['authentication/SaTokenAuthenticationManager.java']);
  return expected;
}

export function validateDistribution(inventory, expected = expectedBinaryOwners()) {
  const errors = [];
  if (!/^[0-9a-f]{64}$/.test(inventory?.boot_sha256 || '') ||
      inventory.boot_sha256 !== inventory?.release_sha256) {
    errors.push('Distribution API JAR differs from the tested Boot executable');
  }
  const libs = inventory?.nested || [];
  const matched = {};
  for (const [id, artifact] of Object.entries(ARTIFACTS)) {
    const found = libs.filter(name =>
      name.startsWith('BOOT-INF/lib/' + artifact + '-') && name.endsWith('.jar'));
    if (found.length !== 1) {
      errors.push('Expected exactly one ' + artifact + ' dependency in Boot package');
    } else {
      matched[id] = found[0];
    }
  }
  const classes = inventory?.class_owners || {};
  for (const [className, owner] of expected.entries()) {
    const seen = classes[className] || [];
    if (seen.length !== 1 || seen[0] !== matched[owner]) {
      errors.push('Relocated Security class has missing, duplicated or wrong binary owner: ' + className);
    }
  }
  for (const [className, seen] of Object.entries(classes)) {
    if (className.startsWith('io/yak/framework/security/') &&
        (!Array.isArray(seen) || seen.length !== 1)) {
      errors.push('Duplicate Security FQCN in executable classpath: ' + className);
    }
  }
  if (Object.keys(classes).length === 0) errors.push('Security classpath inventory is empty');
  return errors;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [bootJar, distribution] = process.argv.slice(2);
  if (!bootJar || !distribution) {
    console.error('Usage: node check-security-distribution-classpath.mjs BOOT_JAR DIST_TAR_GZ');
    process.exitCode = 2;
  } else {
    const result = spawnSync('python3',
      [resolve(ROOT, 'scripts/architecture/probe-security-distribution.py'),
       resolve(bootJar), resolve(distribution)],
      { cwd: ROOT, encoding: 'utf8', maxBuffer: 30 * 1024 * 1024 });
    if (result.status !== 0) {
      console.error(result.stderr || result.error?.message || 'Security distribution probe failed');
      process.exitCode = 1;
    } else {
      try {
        const inventory = JSON.parse(result.stdout);
        const expected = expectedBinaryOwners();
        const errors = validateDistribution(inventory, expected);
        if (errors.length) {
          console.error(errors.slice(0, 50).join('\n'));
          process.exitCode = 1;
        } else {
          console.log('Security distribution classpath passed: ' + expected.size +
            ' moved legacy FQCNs, four canonical modules, no duplicate Security types,' +
            ' matching executable/release SHA-256 ' + inventory.boot_sha256);
        }
      } catch (error) {
        console.error('Security release classpath validation failed: ' + error.message);
        process.exitCode = 1;
      }
    }
  }
}
