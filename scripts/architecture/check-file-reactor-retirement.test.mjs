import test from 'node:test';
import assert from 'node:assert/strict';
import { issues, filesInRepository, ROOT } from './check-file-reactor-retirement.mjs';

function base() {
  return new Map([
    ['data-ops-framework/pom.xml', '<project><modules><module>data-common</module><module>data-security</module></modules></project>'],
    ['data-ops-bom/pom.xml', '<project><dependencyManagement><dependencies><dependency><groupId>io.github.weifuwan</groupId><artifactId>data-common</artifactId></dependency></dependencies></dependencyManagement></project>'],
    ['data-ops-boot/pom.xml', '<project><dependencies><dependency><groupId>io.yak.ops</groupId><artifactId>data-ops-common</artifactId></dependency></dependencies></project>'],
    ['data-ops-boot/src/main/java/io/yak/ops/boot/App.java', 'package io.yak.ops.boot; class App {}'],
    ['data-ops-framework/data-file/pom.xml', '<project><artifactId>data-file</artifactId></project>'],
    ['data-ops-framework/data-file/src/main/java/io/yak/framework/file/YakFileService.java', 'package io.yak.framework.file; class YakFileService {}'],
    ['data-ops-framework/legacy/data-job/pom.xml', '<project><dependencies><dependency><artifactId>data-file</artifactId></dependency></dependencies></project>'],
  ]);
}

test('A8.1 positive fixture: retired standalone library source is preserved but not included in reactor', () => {
  assert.deepEqual(issues(base()), []);
});
test('A8.1 excludes XML comments while rejecting newly readded active reactor module', () => {
  const f = base();
  f.set('data-ops-framework/pom.xml', '<project><modules><!-- <module>data-file</module> --><module>data-common</module></modules></project>');
  assert.deepEqual(issues(f), []);
  f.set('data-ops-framework/pom.xml', '<project><modules><module>data-file</module></modules></project>');
  assert.match(issues(f).join('\n'), /must not be a reactor module/);
});
test('A8.1 rejects direct and managed Maven data-file dependency, regardless of group', () => {
  for (const group of ['io.github.weifuwan', 'io.yak.ops']) {
    const f = base();
    f.set('data-ops-boot/pom.xml', '<project><dependencies><dependency><groupId>'
      + group + '</groupId><artifactId>data-file</artifactId></dependency></dependencies></project>');
    assert.match(issues(f).join('\n'), /data-file dependency/);
  }
  const f = base();
  f.set('data-ops-bom/pom.xml', '<project><dependencyManagement><dependencies><dependency>'
    + '<groupId>io.github.weifuwan</groupId><artifactId>data-file</artifactId>'
    + '</dependency></dependencies></dependencyManagement></project>');
  assert.match(issues(f).join('\n'), /data-ops-bom\/pom.xml.*dependency/);
});
test('A8.1 prevents File class import and reflective auto-configuration reintroduction', () => {
  const f = base();
  f.set('data-ops-boot/src/main/java/io/yak/ops/boot/App.java',
    'import io.yak.framework.file.*; class App {}');
  assert.match(issues(f).join('\n'), /old Framework File namespace/);
  f.set('data-ops-boot/src/main/java/io/yak/ops/boot/App.java', 'class App {}');
  f.set('data-ops-boot/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports',
    'io.yak.framework.file.SomeConfiguration');
  assert.match(issues(f).join('\n'), /old Framework File namespace/);
});
test('A8.1 historical File sources and legacy job do not imply current application consumers', () => {
  const f = base();
  f.set('data-ops-framework/data-file/src/main/java/io/yak/framework/file/YakFileService.java',
    'package io.yak.framework.file; import io.yak.framework.file.FileStorage; class YakFileService {}');
  f.set('data-ops-framework/legacy/data-job/src/main/java/Legacy.java',
    'import io.yak.framework.file.FileStorage; class Legacy {}');
  assert.deepEqual(issues(f), []);
});
test('A8.1 guard fails closed when the parent POM cannot be inspected', () => {
  const f = base();
  f.delete('data-ops-framework/pom.xml');
  assert.match(issues(f).join('\n'), /unable to verify/);
});
test('A8.1 checked-out repository actually has no active File dependency or module', () => {
  assert.deepEqual(issues(filesInRepository(ROOT)), []);
});
