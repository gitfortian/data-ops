import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

import {
  decisionFor, declaredJavaTypes, retentionReasons, scanBackend,
  printableReport,
} from './backend-p2-reachability-audit.mjs';

const PROD = 'data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/service/';
const LEGACY = 'data-ops-framework/legacy/data-job/';
const read = p => readFileSync(p, 'utf8');

test('audits production Java and tests across the full Maven monorepo instead of one domain', () => {
  const result = scanBackend();
  assert.ok(result.sourceFiles >= 2000, result.sourceFiles);
  assert.ok(result.testFiles >= 400, result.testFiles);
  assert.ok(result.moduleRoots.length >= 30, result.moduleRoots.length);
  assert.ok(result.trackedReferenceFiles >= result.sourceFiles);
  assert.equal(result.candidates.length, result.sourceFiles);
  assert.equal(Object.values(result.disposition).reduce((a, b) => a + b, 0),
    result.sourceFiles);
});

test('the audit cannot mistake absence of a text match for safe deletion', () => {
  assert.equal(decisionFor(0, []), 'UNRESOLVED_NO_TEXT_MATCH_REVIEW');
  assert.equal(decisionFor(0, ['framework-discovered']),
    'IMPLICIT_ENTRYPOINT_KEEP_UNTIL_PROVEN');
  assert.equal(decisionFor(1, []), 'REFERENCED_KEEP');
  assert.equal(decisionFor(0, [], true), 'AMBIGUOUS_NAME_REVIEW');
  assert.ok(!Object.keys(scanBackend().disposition).some(x => /SAFE_TO_DELETE/.test(x)));
});

test('Java declaration and implicit framework markers are classified conservatively', () => {
  const text = 'package a.b; @RestController public class LiveEndpoint {}';
  assert.deepEqual(declaredJavaTypes(text), [{ name: 'LiveEndpoint', fqn: 'a.b.LiveEndpoint' }]);
  assert.ok(retentionReasons('module/src/main/java/a/b/LiveEndpoint.java', text)
    .includes('framework-discovered'));
  assert.ok(retentionReasons('module/src/main/java/a/b/ExampleMapper.java',
    'public interface ExampleMapper {}').length > 0);
  assert.ok(retentionReasons(LEGACY + 'src/main/java/x/Helper.java', 'class Helper {}')
    .includes('independent-legacy-release'));
});

test('explicit Boot API, Project compatibility, Spring gateways and Modeling contracts remain present', () => {
  for (const file of [
    'data-ops-boot/src/main/java/io/yak/ops/boot/controller/TestController.java',
    'data-ops-boot/src/main/java/io/yak/ops/boot/project/ProjectCompatibilityCoordinator.java',
    'data-ops-business/data-ops-business-analysis/src/main/java/io/yak/ops/business/analysis/gateway/dataset/DatasetAnalysisAdapter.java',
    'data-ops-business/data-ops-business-dashboard/src/main/java/io/yak/ops/business/dashboard/gateway/analysis/AnalysisDashboardAdapter.java',
    PROD + 'ModelImpactResolver.java',
  ]) assert.ok(existsSync(file), file);
  assert.ok(existsSync(LEGACY + 'pom.xml'));
  assert.ok(existsSync('scripts/architecture/framework-legacy.test.mjs'));
  assert.ok(existsSync('scripts/db/check-migration-history.mjs'));
});

test('all five Modeling no-op impact adapters keep their public types and empty results', () => {
  const names = [
    'DefaultImpactRelationshipAdapter', 'PersistenceBackedImpactRelationshipAdapter',
    'LogicalEntityImpactRelationshipAdapter', 'LogicalModelImpactRelationshipAdapter',
    'MappingImpactRelationshipAdapter',
  ];
  for (const name of names) {
    const file = PROD + name + '.java';
    const src = read(file);
    assert.ok(src.includes('public class ' + name + ' implements ImpactRelationshipAdapter'),
      file);
    assert.ok(src.includes('return Collections.emptyList();'), file);
    assert.ok(!src.includes('if (objectType == null || objectId == null)'), file);
  }
});

test('interface defaults stay behavior-identical, with executable JUnit guards', () => {
  for (const name of ['ImpactRelationshipPersistenceAdapter', 'ImpactGovernanceService']) {
    const file = PROD + name + '.java';
    const src = read(file);
    assert.ok(src.includes('return Collections.emptyList();'), file);
    assert.ok(!src.includes('if (objectType == null || objectId == null)'), file);
  }
  const testRoot = 'data-ops-business/data-ops-business-modeling/src/test/java/' +
    'io/yak/ops/business/modeling/service/';
  for (const name of [
    'ImpactRelationshipAdapterTest', 'ImpactRelationshipPersistenceAdapterTest',
    'ImpactGovernanceServiceTest',
  ]) assert.ok(existsSync(testRoot + name + '.java'));
});

test('the full candidate report remains explicitly read-only and provides risk categories', () => {
  const report = printableReport();
  assert.equal(report.schema, 'backend-p2-audit-v1');
  assert.ok(report.safety.includes('NEVER authorization to delete'));
  assert.ok(report.dispositions);
  assert.ok(report.moduleRootCount >= 30);
  assert.ok(Array.isArray(report.lowReference));
  assert.ok(Array.isArray(report.protectedSamples));
});
