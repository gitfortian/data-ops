package io.yak.ops.business.lifecycle.binding;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.lifecycle.dao.model.LifecycleDispatchRecordPO;
import io.yak.ops.business.lifecycle.dao.model.LifecyclePolicyPO;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.ModelState;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** D5 状态机单测(ticket 82):deriveState 五分支。 */
class ModelTtlBindingServiceStateTest {

  private static LifecyclePolicyPO policy(long id, LocalDateTime updateTime) {
    LifecyclePolicyPO po = new LifecyclePolicyPO();
    po.setId(id);
    po.setUpdateTime(updateTime);
    return po;
  }

  private static LifecycleDispatchRecordPO record(String status, Long policyId,
      LocalDateTime policyUpdatedAt) {
    LifecycleDispatchRecordPO po = new LifecycleDispatchRecordPO();
    po.setStatus(status);
    po.setPolicyId(policyId);
    po.setPolicyUpdatedAt(policyUpdatedAt);
    return po;
  }

  @Test
  void neverDispatchedIsDrift() {
    assertThat(ModelTtlBindingService.deriveState(policy(1L, null), null, false))
        .isEqualTo(ModelState.DRIFT);
  }

  @Test
  void failedRetryingExhaustedAllMapToFailed() {
    for (String status : new String[] {"FAILED", "RETRYING", "EXHAUSTED"}) {
      assertThat(ModelTtlBindingService.deriveState(
          policy(1L, null), record(status, 1L, null), false))
          .as(status)
          .isEqualTo(ModelState.FAILED);
    }
  }

  @Test
  void policySwitchMakesStateDrift() {
    assertThat(ModelTtlBindingService.deriveState(
        policy(2L, null), record("SUCCESS", 1L, null), false))
        .isEqualTo(ModelState.DRIFT);
  }

  @Test
  void policyEditedAfterDispatchIsDrift() {
    LocalDateTime dispatchedAt = LocalDateTime.of(2026, 9, 1, 10, 0);
    assertThat(ModelTtlBindingService.deriveState(
        policy(1L, dispatchedAt.plusDays(1)), record("SUCCESS", 1L, dispatchedAt), false))
        .isEqualTo(ModelState.DRIFT);
  }

  @Test
  void samePolicyNotEditedAfterDispatchIsApplied() {
    LocalDateTime dispatchedAt = LocalDateTime.of(2026, 9, 1, 10, 0);
    assertThat(ModelTtlBindingService.deriveState(
        policy(1L, dispatchedAt), record("SUCCESS", 1L, dispatchedAt), false))
        .isEqualTo(ModelState.APPLIED);
  }

  @Test
  void virtualPolicySkipsIdComparison() {
    // 合成虚拟策略无主键:不与记录 policyId 比较,只看时间/状态
    assertThat(ModelTtlBindingService.deriveState(
        policy(0L, null), record("SUCCESS", 999L, null), true))
        .isEqualTo(ModelState.APPLIED);
  }
}
