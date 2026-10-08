import test from 'node:test';
import assert from 'node:assert/strict';
import { copyFileSync, mkdirSync, mkdtempSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync, spawnSync } from 'node:child_process';

const scripts = dirname(fileURLToPath(import.meta.url));
const legacy = 'data-ops-framework/legacy/data-job';

function fixture(t) {
  const root = realpathSync(mkdtempSync(join(tmpdir(), 'data-ops-framework-boundary-')));
  t.after(() => {
    const temporary = realpathSync(tmpdir());
    assert.equal(dirname(root), temporary);
    rmSync(root, { recursive: true, force: true });
  });
  const write = (file, text) => {
    const destination = join(root, file);
    mkdirSync(dirname(destination), { recursive: true });
    writeFileSync(destination, text);
  };
  for (const file of ['check-boundaries.mjs', 'import-boundaries.mjs']) {
    const destination = join(root, 'scripts/architecture', file);
    mkdirSync(dirname(destination), { recursive: true });
    copyFileSync(join(scripts, file), destination);
  }
  write('scripts/architecture/legacy-shared-persistence.json', JSON.stringify({ files: [], imports: [] }));
  write('pom.xml', '<project><artifactId>data-ops</artifactId><modules><module>data-ops-framework</module><module>data-ops-boot</module></modules></project>');
  write('data-ops-framework/pom.xml', '<project><artifactId>data-ops-framework-parent</artifactId></project>');
  write('data-ops-boot/pom.xml', '<project><artifactId>data-ops-boot</artifactId></project>');
  write('data-ops-boot/src/main/java/App.java', 'package io.yak.ops.boot;\nclass App {}');
  write(`${legacy}/pom.xml`, '<project><groupId>io.yak</groupId><artifactId>data-job-spring-boot-starter</artifactId><version>1.0.31</version></project>');
  // Legacy code must not be inspected as current application code after directory nesting.
  write(`${legacy}/src/main/java/Legacy.java`, 'package com.yak.job;\nimport io.yak.ops.common.bean.po.LegacyPO;\nclass Legacy {}');
  execFileSync('git', ['init', '--quiet'], { cwd: root });
  const check = () => spawnSync(process.execPath, [resolve(root, 'scripts/architecture/check-boundaries.mjs')], { cwd: root, encoding: 'utf8' });
  return { write, check };
}

test('nested legacy sources remain outside the application inventory', t => {
  const { check } = fixture(t);
  const result = check();
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /3 reactor entries, 1 production Java files/);
});

test('current application rejects explicit, wildcard and static legacy imports', t => {
  const { write, check } = fixture(t);
  for (const imported of ['com.yak.job.YakJobProperties', 'com.yak.job.*', 'static com.yak.job.utils.Assert.*']) {
    write('data-ops-boot/src/main/java/App.java', `package io.yak.ops.boot;\nimport ${imported};\nclass App {}`);
    const result = check();
    assert.equal(result.status, 1, imported);
    assert.match(result.stderr, /current application must not import legacy com\.yak\.job\./);
  }
});

test('current reactor rejects the legacy starter as a production dependency', t => {
  const { write, check } = fixture(t);
  write('data-ops-boot/pom.xml', '<project><artifactId>data-ops-boot</artifactId><dependencies><dependency><groupId>io.yak</groupId><artifactId>data-job-spring-boot-starter</artifactId><version>1.0.31</version></dependency></dependencies></project>');
  const result = check();
  assert.equal(result.status, 1, result.stderr);
  assert.match(result.stderr, /must not depend on legacy data-job-spring-boot-starter/);
});

test('legacy cannot enter the reactor directly or through its aggregate', t => {
  const { write, check } = fixture(t);
  write('data-ops-framework/legacy/pom.xml', '<project><artifactId>legacy-parent</artifactId><modules><module>data-job</module></modules></project>');
  for (const module of ['legacy/data-job', 'legacy']) {
    write('data-ops-framework/pom.xml', `<project><artifactId>data-ops-framework-parent</artifactId><modules><module>${module}</module></modules></project>`);
    const result = check();
    assert.equal(result.status, 1, result.stderr);
    assert.match(result.stderr, /legacy framework must remain outside the current Maven reactor/);
  }
});


test('production Mapper XML is rejected while standalone legacy and Flyway files remain allowed', t => {
  const { write, check } = fixture(t);
  write(`${legacy}/src/main/resources/mapper/yak-job/YakTaskMapper.xml`, '<mapper/>');
  write('data-ops-boot/src/main/resources/db/migration/V1__schema.sql', 'SELECT 1;');
  write('data-ops-boot/src/main/resources/mybatis-config.xml', '<configuration/>');
  const allowed = check();
  assert.equal(allowed.status, 0, allowed.stderr);

  write('data-ops-boot/src/main/resources/mapper/demo/TaskMapper.xml', '<mapper/>');
  const forbidden = check();
  assert.equal(forbidden.status, 1, forbidden.stderr);
  assert.match(forbidden.stderr, /TaskMapper[.]xml: production Mapper XML is not allowed/);
});

test('Mapper XML cannot bypass the guard by moving outside the mapper directory', t => {
  const { write, check } = fixture(t);
  write('data-ops-boot/src/main/resources/mybatis/TaskMapper.xml', '<mapper/>');
  const result = check();
  assert.equal(result.status, 1, result.stderr);
  assert.match(result.stderr, /mybatis[/]TaskMapper[.]xml: production Mapper XML is not allowed/);
});
