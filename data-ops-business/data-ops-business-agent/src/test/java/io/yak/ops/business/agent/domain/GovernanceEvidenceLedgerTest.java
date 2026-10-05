package io.yak.ops.business.agent.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GovernanceEvidenceLedgerTest {
  @Test void onlyRegisteredLinksSurviveAndUnknownCitationsRejectWholeAnswer() {
    var ledger = new GovernanceEvidenceLedger();
    var fact = ledger.register("QUALITY", "exec-7", "OK", null, "/data-quality/execution/exec-7");
    String answer = ledger.validateAnswer("规则未通过 [" + fact.id() + "] [点击](https://attacker.invalid) <a href='/fake'>伪造</a>");
    assertTrue(answer.contains("规则未通过"));
    assertTrue(answer.contains("source") || answer.contains("unknown"));
    assertTrue(answer.contains("/data-quality/execution/exec-7"));
    assertFalse(answer.contains("attacker.invalid"));
    assertFalse(answer.contains("/fake"));
    assertTrue(ledger.validateAnswer("全部健康 [E00000000]").contains("不存在的证据"));
    assertFalse(ledger.validateAnswer("全部健康 [E00000000]").contains("全部健康"));
    assertTrue(ledger.validateAnswer("没有引用的治理结论").contains("未引用本轮证据"));
  }

  @Test void deniedAndUnavailableEvidenceCannotSupportHealthyConclusion() {
    var ledger = new GovernanceEvidenceLedger();
    var denied = ledger.register("SECURITY", "7", "PERMISSION_DENIED", null, "/data-asset/detail/7");
    String answer = ledger.validateAnswer("这个资产没有安全风险 [" + denied.id() + "]");
    assertFalse(answer.contains("没有安全风险"));
    assertTrue(answer.contains("没有可读取"));
    assertTrue(answer.contains("PERMISSION_DENIED"));
    var another = new GovernanceEvidenceLedger();
    another.register("ASSET", "8", "OK", null, "/data-asset/detail/8");
    assertTrue(another.validateAnswer("结论 [" + denied.id() + "]").contains("不存在的证据"));
  }

  @Test void turnInputRemainsCompatibleAndResumeCarriesSelectedTarget() {
    var old = io.yak.ops.business.agent.repository.support.TurnInputCodec.decode(
        "{\"userMessageId\":\"u\",\"assistantMessageId\":\"a\",\"message\":\"解释资产\",\"feedbacks\":[]}");
    assertNull(old.governanceTarget());
    var target = new GovernanceTarget(7L, null);
    var upgraded = old.withTarget(target);
    assertEquals(target, io.yak.ops.business.agent.repository.support.TurnInputCodec.decode(
        io.yak.ops.business.agent.repository.support.TurnInputCodec.encode(upgraded)).governanceTarget());
    assertThrows(IllegalArgumentException.class, () -> new GovernanceTarget(7L, "exec-7"));
  }
}
