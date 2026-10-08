import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import {
  A8_BASELINE_SHA, eligible, references, scanPom, collect, compare,
} from './framework-coupling.mjs';

const securityDependency = '<dependency><groupId>io.github.weifuwan</groupId>'
  + '<artifactId>data-security-spring-boot-starter</artifactId></dependency>';
const oldJava = 'package demo;\nimport io.yak.framework.security.auth.Account;\n'
  + 'class Demo { io.yak.framework.security.auth.Account account; }\n';

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'yak-a8-coupling-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const write = (path, content) => {
    const dest = join(root, path);
    mkdirSync(dirname(dest), { recursive: true });
    writeFileSync(dest, content);
  };
  const git = (...args) => execFileSync('git', args, {
    cwd: root, encoding: 'utf8',
  }).trim();
  git('init', '-q');
  write('pom.xml', '<project><artifactId>data-ops</artifactId></project>');
  write('data-ops-boot/pom.xml', '<project>' + securityDependency + '</project>');
  write('data-ops-boot/src/main/java/demo/Demo.java', oldJava);
  write('data-ops-framework/legacy/data-job/src/main/java/com/yak/job/Legacy.java',
    'import io.yak.framework.security.Legacy;\n');
  git('add', '.');
  git('-c', 'user.name=A8 Test', '-c', 'user.email=a8@example.test',
    'commit', '-qm', 'snapshot');
  return { root, git, write, baseline: git('rev-parse', 'HEAD') };
}

test('baseline anchor is fixed and all enabled surface classes are explicit', () => {
  assert.match(A8_BASELINE_SHA, /^[a-f0-9]{40}$/);
  assert.equal(eligible('pom.xml'), 'pom');
  assert.equal(eligible('data-ops-business/data-ops-business-workflow/pom.xml'), 'pom');
  assert.equal(eligible('data-ops-boot/src/main/java/Demo.java'), 'java');
  assert.equal(eligible('data-ops-boot/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports'), 'resource');
  assert.equal(eligible('.github/workflows/architecture-checks.yml'), 'assembly');
  assert.equal(eligible('data-ops-framework/legacy/data-job/pom.xml'), false);
  assert.equal(eligible('data-ops-boot/src/test/java/DemoTest.java'), null);
});

test('POM recognizes direct, managed, plugin and parent coordinates; excludes comments and unrelated group', () => {
  const xml = '<project><parent><groupId>io.github.weifuwan</groupId>'
    + '<artifactId>data-ops-framework-parent</artifactId></parent>'
    + '<dependencyManagement><dependencies>' + securityDependency
    + '</dependencies></dependencyManagement>'
    + '<dependencies>' + securityDependency
    + '<dependency><groupId>io.yak.ops</groupId><artifactId>data-common</artifactId></dependency>'
    + '</dependencies><build><plugins><plugin><groupId>io.github.weifuwan</groupId>'
    + '<artifactId>data-schedule-plugin-quartz</artifactId></plugin></plugins></build>'
    + '<!-- ' + securityDependency + ' --></project>';
  const actual = scanPom(xml);
  assert.equal(actual.filter(x => x.includes('data-security')).length, 2);
  assert.ok(actual.some(x => x.startsWith('maven:parent:') && x.includes('data-ops-framework-parent')));
  assert.ok(actual.some(x => x.startsWith('maven:plugin:') && x.includes('data-schedule-plugin-quartz')));
  assert.equal(actual.some(x => x.includes('io.yak.ops:data-common')), false);
  assert.equal(actual.length, 4);
});

test('production Java captures explicit, wildcard, static, FQCN and multiple uses', () => {
  const java = 'import io.yak.framework.security.User;\n'
    + 'import io.yak.framework.schedule.api.*;\n'
    + 'import static io.yak.framework.workflow.engine.X.KEY;\n'
    + 'class Test { io.yak.framework.security.User user; }';
  const found = references('data-ops-common/src/main/java/Demo.java', java);
  assert.deepEqual(found, [
    'symbol:io.yak.framework.security.User',
    'symbol:io.yak.framework.schedule.api.*',
    'symbol:io.yak.framework.workflow.engine.X.KEY',
    'symbol:io.yak.framework.security.User',
  ]);
  assert.deepEqual(references('data-ops-boot/src/test/java/Test.java', java), []);
});

test('resource references are tracked without scanning docs or legacy Java', () => {
  const text = 'io.yak.framework.security.autoconfigure.YakSecurityAutoConfiguration';
  assert.deepEqual(references(
    'data-ops-framework/data-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports',
    text), ['symbol:' + text]);
  assert.deepEqual(references('docs/architecture/A8.md', text), []);
  assert.deepEqual(references(
    'data-ops-framework/legacy/data-job/src/main/java/com/yak/job/Demo.java', text), []);
});

test('exact pinned baseline permits removals but blocks additions, relocation and duplicate uses', t => {
  const { root, baseline, write } = fixture(t);
  const original = collect(root, baseline);
  assert.equal(compare(original, collect(root)).length, 0);
  write('data-ops-boot/src/main/java/demo/Demo.java',
    'package demo;\nclass Demo {}\n');
  assert.equal(compare(original, collect(root)).length, 0, 'removal is allowed');
  write('data-ops-boot/src/main/java/demo/Demo.java',
    oldJava + 'import io.yak.framework.security.auth.Other;\n');
  assert.match(JSON.stringify(compare(original, collect(root))), /Other/);
  write('data-ops-boot/src/main/java/demo/Demo.java', oldJava
    + 'io.yak.framework.security.auth.Account duplicate;\n');
  assert.equal(compare(original, collect(root))[0].current, 3);
  write('data-ops-boot/src/main/java/demo/Demo.java', oldJava);
  write('data-ops-business/data-ops-business-metric/src/main/java/Extra.java',
    'import io.yak.framework.security.auth.Account;\n');
  const moved = compare(original, collect(root));
  assert.ok(moved.some(x => x.path.endsWith('Extra.java')));
});

test('new Maven dependency and new auto-configuration entry fail the reference gate', t => {
  const { root, baseline, write } = fixture(t);
  const original = collect(root, baseline);
  write('data-ops-boot/pom.xml', '<project>' + securityDependency
    + '<dependency><groupId>io.github.weifuwan</groupId>'
    + '<artifactId>data-workflow-engine</artifactId></dependency></project>');
  write('data-ops-boot/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports',
    'io.yak.framework.security.autoconfigure.NewAutoConfiguration\n');
  const introduced = compare(original, collect(root));
  assert.equal(introduced.length, 2);
  assert.ok(introduced.some(x => x.reference.includes('data-workflow-engine')));
  assert.ok(introduced.some(x => x.reference.includes('NewAutoConfiguration')));
});

test('missing historical anchor is a hard error, never auto-rebaselined', t => {
  const { root } = fixture(t);
  assert.throws(() => collect(root, A8_BASELINE_SHA), /git rev-parse --verify failed/);
});
