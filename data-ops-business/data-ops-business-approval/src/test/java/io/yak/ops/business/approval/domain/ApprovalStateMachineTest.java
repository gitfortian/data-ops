package io.yak.ops.business.approval.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 流转规则单测:级间串行,末级通过即终态。 */
class ApprovalStateMachineTest {

  @Test
  void approveAdvancesUntilMaxLevel() {
    ApprovalStateMachine.OnApprove first = ApprovalStateMachine.onApprove(1, 2);
    assertFalse(first.finished());
    assertEquals(2, first.nextLevelNo());

    ApprovalStateMachine.OnApprove last = ApprovalStateMachine.onApprove(2, 2);
    assertTrue(last.finished());

    ApprovalStateMachine.OnApprove single = ApprovalStateMachine.onApprove(1, 1);
    assertTrue(single.finished());
  }
}
