package io.yak.ops.business.quality.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class QualityDomainTest {

  @Test
  void legacyTableMonitorSummaryConstructorPreservesItsCounts() {
    LocalDateTime lastRunTime = LocalDateTime.parse("2026-09-24T10:00:00");

    QualityDomain.TableMonitorSummary summary = new QualityDomain.TableMonitorSummary(
        "orders", 7L, "Orders", 3, 11, CheckResult.PASSED, lastRunTime);

    assertThat(summary.monitorCount()).isEqualTo(3);
    assertThat(summary.ruleCount()).isEqualTo(11);
    assertThat(summary.enabledMonitorCount()).isZero();
    assertThat(summary.lastExecutionNo()).isNull();
    assertThat(summary.lastResult()).isEqualTo(CheckResult.PASSED);
    assertThat(summary.lastRunTime()).isEqualTo(lastRunTime);
  }
}
