import test from 'node:test';
import assert from 'node:assert/strict';
import { ENUM_NAMES, findViolations, scanRepository } from './check-common-security-corridor.mjs';

const common = 'data-ops-common/src/main/java/io/yak/ops/common/';
const security = 'data-ops-framework/data-security/';
function fixture() {
  return new Map([
    [common + 'enums/metric/MetricErrorCode.java',
      'import io.yak.framework.common.ErrorCode; public enum MetricErrorCode implements ErrorCode { A }'],
    [common + 'schedule/YakScheduleGateway.java',
      'import io.yak.framework.schedule.api.ScheduleManager;'],
    [security + 'pom.xml', '<project><dependencies><dependency><groupId>io.github.weifuwan</groupId>'
      + '<artifactId>data-common</artifactId></dependency></dependencies></project>'],
    [security + 'src/main/java/io/yak/framework/security/Authentication.java',
      'package io.yak.framework.security; class Authentication {}'],
  ]);
}
test('A8.1c product legacy corridor contains the 12 audited enum owners', () => {
  assert.equal(ENUM_NAMES.length, 12);
  assert.deepEqual(findViolations(fixture()), []);
});
test('A8.1c rejects new Common -> Framework Common types even in existing owner', () => {
  const f = fixture();
  f.set(common + 'enums/metric/MetricErrorCode.java',
    'import io.yak.framework.common.ErrorCode;\nimport io.yak.framework.common.Result;');
  assert.match(findViolations(f).join('\n'), /unapproved.*Result/);
});
test('A8.1c rejects new Framework references in an unrelated product Common class', () => {
  const f = fixture();
  f.set(common + 'util/MyUtil.java', 'import io.yak.framework.common.ErrorCode;');
  assert.match(findViolations(f).join('\n'), /MyUtil\.java: unapproved/);
});
test('A8.1c rejects newly widened scheduler surface', () => {
  const f = fixture();
  f.set(common + 'schedule/YakScheduleGateway.java',
    'import io.yak.framework.schedule.api.ScheduleManager;\n'
    + 'import io.yak.framework.schedule.core.QuartzScheduler;');
  assert.match(findViolations(f).join('\n'), /QuartzScheduler/);
});
test('A8.1c blocks reverse Security -> Product code including fully-qualified forms', () => {
  const f = fixture();
  f.set(security + 'src/main/java/io/yak/framework/security/Authentication.java',
    'import io.yak.ops.common.bean.dto.resource.ResourceDTO;\n'
    + 'class Authentication { io.yak.ops.business.security.dao.SecretDao dao; }');
  const problems = findViolations(f);
  assert.equal(problems.length, 2);
  assert.ok(problems.every(v => /reverse-depend/.test(v)));
});
test('A8.1c blocks Maven Security -> Common / Boot and ignores harmless XML comments', () => {
  const f = fixture();
  const dep = '<dependency><groupId>io.yak.ops</groupId>'
    + '<artifactId>data-ops-common</artifactId></dependency>';
  f.set(security + 'pom.xml', '<project><dependencies><!-- ' + dep
    + ' --></dependencies></project>');
  assert.deepEqual(findViolations(f), []);
  f.set(security + 'pom.xml', '<project><dependencies>' + dep + '</dependencies></project>');
  assert.match(findViolations(f).join('\n'), /must not depend on product/);
});
test('A8.1c real repository consumer corridor conforms to the audited surface', () => {
  assert.deepEqual(findViolations(scanRepository()), []);
});
