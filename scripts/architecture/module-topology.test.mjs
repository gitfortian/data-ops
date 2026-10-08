import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { audit, buildMavenEdges, moduleRole, parsePom, readReactor, scanJavaImports } from './module-topology.mjs';

const project = (body) => '<project>' + body + '</project>';
const parent = '<parent><groupId>io.yak.ops</groupId><artifactId>root</artifactId><version>1</version></parent>';
const modulePom = (name, extra = '') => project(parent + '<artifactId>' + name + '</artifactId>' + extra);
const dep = (name, scope = null) => '<dependency><groupId>io.yak.ops</groupId><artifactId>'
  + name + '</artifactId>' + (scope ? '<scope>' + scope + '</scope>' : '') + '</dependency>';
const write = (root, file, content) => {
  const full = path.join(root, file);
  fs.mkdirSync(path.dirname(full), { recursive: true });
  fs.writeFileSync(full, content);
};
const createFixture = () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'data-ops-topology-'));
  write(root, 'pom.xml', project('<groupId>io.yak.ops</groupId><artifactId>root</artifactId>'
    + '<packaging>pom</packaging><modules><module>data-ops-core</module>'
    + '<module>data-ops-business</module><module>data-ops-boot</module>'
    + '<module>data-ops-dist</module></modules>'));
  write(root, 'data-ops-core/pom.xml', modulePom('data-ops-core'));
  write(root, 'data-ops-business/pom.xml', modulePom('data-ops-business',
    '<packaging>pom</packaging><modules><module>data-ops-business-asset</module></modules>'));
  write(root, 'data-ops-business/data-ops-business-asset/pom.xml',
    modulePom('data-ops-business-asset', '<dependencies>'
      + dep('data-ops-core') + dep('data-ops-boot', 'test') + '</dependencies>'));
  write(root, 'data-ops-boot/pom.xml', modulePom('data-ops-boot',
    '<dependencies>' + dep('data-ops-business-asset') + '</dependencies>'));
  write(root, 'data-ops-dist/pom.xml', modulePom('data-ops-dist',
    '<packaging>pom</packaging><dependencies>' + dep('data-ops-boot') + '</dependencies>'));
  const core = 'data-ops-core/src/main/java/io/yak/ops/core/CoreType.java';
  const business = 'data-ops-business/data-ops-business-asset/src/main/java/io/yak/ops/business/asset/AssetReader.java';
  write(root, core, 'package io.yak.ops.core;\npublic class CoreType { public static int FIELD = 0; }\n');
  write(root, business, 'package io.yak.ops.business.asset;\n'
    + 'import io.yak.ops.core.CoreType;\nimport static io.yak.ops.core.CoreType.FIELD;\n'
    + 'public class AssetReader {}\n');
  return { root, files: [core, business] };
};

test('POM parser ignores commented modules, dependencyManagement and build plugin dependencies', () => {
  const parsed = parsePom(project(parent + '<artifactId>data-ops-boot</artifactId>'
    + '<!-- <modules><module>not-real</module></modules> -->'
    + '<modules><module>real</module></modules>'
    + '<dependencyManagement><dependencies>' + dep('managed') + '</dependencies></dependencyManagement>'
    + '<build><plugins><plugin><dependencies>' + dep('plugin') + '</dependencies></plugin></plugins></build>'
    + '<dependencies>' + dep('actual') + dep('only-for-tests', 'test') + '</dependencies>'));
  assert.equal(parsed.groupId, 'io.yak.ops');
  assert.deepEqual(parsed.modules, ['real']);
  assert.deepEqual(parsed.dependencies.map((d) => [d.artifactId, d.scope]),
    [['actual', 'compile'], ['only-for-tests', 'test']]);
});

test('role classification is by actual module ownership, not by a Service name', () => {
  assert.equal(moduleRole('data-ops-boot'), 'boot');
  assert.equal(moduleRole('data-ops-dist'), 'distribution');
  assert.equal(moduleRole('data-ops-framework/data-security'), 'framework');
  assert.equal(moduleRole('data-ops-business/data-ops-business-mdm'), 'business-domain');
  assert.equal(moduleRole('data-ops-plugins/data-ops-plugin-task'), 'plugin');
});

test('reactor, scoped Maven edges, resolved production imports and non-replaceable assembly edge', () => {
  const { root, files } = createFixture();
  try {
    const modules = readReactor(root);
    assert.equal(modules.length, 6);
    const edges = buildMavenEdges(modules);
    assert.equal(edges.length, 4);
    assert.ok(edges.some((e) => e.from.endsWith('data-ops-business-asset')
      && e.to === 'data-ops-boot' && e.scope === 'test'));
    assert.ok(edges.some((e) => e.from === 'data-ops-dist'
      && e.to === 'data-ops-boot' && e.scope === 'compile'));
    const imports = scanJavaImports(root, modules, files);
    assert.equal(imports.productionJavaFiles, 2);
    assert.equal(imports.edges.length, 1);
    assert.equal(imports.edges[0].from,
      'data-ops-business/data-ops-business-asset');
    assert.equal(imports.edges[0].to, 'data-ops-core');
    assert.equal(imports.edges[0].importCount, 2);
    const report = audit(root, files);
    assert.equal(report.businessModuleCount, 1);
    assert.equal(report.mavenEdges.length, 4);
    assert.equal(report.javaImportEdges.length, 1);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('incomplete reactor references fail closed instead of silently omitting modules', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'data-ops-broken-reactor-'));
  try {
    write(root, 'pom.xml', project('<groupId>io.yak.ops</groupId><artifactId>root</artifactId>'
      + '<modules><module>missing</module></modules>'));
    assert.throws(() => readReactor(root), /Missing reactor POM/);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
