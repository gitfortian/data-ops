package io.yak.ops.business.quality.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.execution.QualityExecutionReader;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QualityAssetSectionSummaryReaderTest {

  private final QualityTableAssetReader tableAssetReader = mock(QualityTableAssetReader.class);
  private final QualityMonitorReader monitorReader = mock(QualityMonitorReader.class);
  private final QualityExecutionReader executionReader = mock(QualityExecutionReader.class);
  private final QualityAssetSectionSummaryReader reader = new QualityAssetSectionSummaryReader(
      tableAssetReader, monitorReader, executionReader);

  @Test
  void confirmedRegisteredTableWithoutMonitorReturnsNotRunSummary() {
    when(tableAssetReader.isRegistered(7L, "sales", "public", "orders")).thenReturn(true);
    when(monitorReader.tableSummaries(7L, "sales", "public", "orders")).thenReturn(List.of());

    QualityAssetSectionSummary result = reader.read(7L, "sales", "public", "orders");

    assertThat(result).isEqualTo(new QualityAssetSectionSummary(true, null, 0, 0, null));
    verify(executionReader).findLatestForTarget(7L, "sales", "public", "orders");
  }

  @Test
  void latestExecutionSummaryRetainsLifecycleConclusionCountsAndTimes() {
    LocalDateTime queuedAt = LocalDateTime.parse("2026-09-23T10:00:00");
    LocalDateTime finishedAt = LocalDateTime.parse("2026-09-23T10:01:00");
    when(tableAssetReader.isRegistered(7L, "sales", "public", "orders")).thenReturn(true);
    when(monitorReader.tableSummaries(7L, "sales", "public", "orders")).thenReturn(List.of(
        new TableMonitorSummary("orders", 9L, "Orders", 2, 4, 1, "QX-1", null, null)));
    Execution execution = new Execution(1L, "QX-1", 9L, "Orders", 7L, "sales", "sales",
        "public", "orders", "orders", null, ExecutionStatus.SUCCESS, CheckResult.NOT_PASSED,
        4, 2, 1, 1, "alice",
        queuedAt, null, finishedAt, 1000L, null, List.of());
    when(executionReader.findLatestForTarget(7L, "sales", "public", "orders"))
        .thenReturn(Optional.of(execution));

    QualityAssetSectionSummary result = reader.read(7L, "sales", "public", "orders");

    assertThat(result.registered()).isTrue();
    assertThat(result.monitorId()).isEqualTo(9L);
    assertThat(result.monitorCount()).isEqualTo(2);
    assertThat(result.enabledMonitorCount()).isEqualTo(1);
    assertThat(result.latestExecution()).satisfies(latest -> {
      assertThat(latest.executionNo()).isEqualTo("QX-1");
      assertThat(latest.issueCount()).isEqualTo(2);
      assertThat(latest.queuedAt()).isEqualTo(queuedAt);
      assertThat(latest.finishedAt()).isEqualTo(finishedAt);
    });
  }
}
