package io.yak.ops.business.quality.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionMapSummary;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import java.util.Map;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class QualityAssetSectionProviderTest {

  private final QualityAssetSectionSummaryReader summaryReader =
      mock(QualityAssetSectionSummaryReader.class);
  private final QualityAssetSectionProvider provider = new QualityAssetSectionProvider(summaryReader);

  @Test
  void qualityOwnedProviderReturnsMonitorProjectionForPhysicalTable() {
    SectionContext context = physicalTableContext();
    when(summaryReader.read(7L, "sales", "public", "orders"))
        .thenReturn(new QualityAssetSectionSummary(true, 9L, 2, 1, null));

    var result = provider.query(context);

    assertThat(provider.sectionType()).isEqualTo(SectionType.QUALITY);
    assertThat(provider.supports(context)).isTrue();
    assertThat(result.ownerDomain()).isEqualTo("QUALITY");
    assertThat(result.status()).isEqualTo(SectionStatus.OK);
    assertThat(((SectionMapSummary) result.summary()).values())
        .containsEntry("registered", true)
        .containsEntry("monitorId", 9L)
        .containsEntry("monitorCount", 2)
        .containsEntry("enabledMonitorCount", 1);
    assertThat(result.actions().get(0).target()).isEqualTo("/data-quality/monitor/9");
    verify(summaryReader).read(7L, "sales", "public", "orders");
  }

  @Test
  void confirmedAbsenceIsEmptyAndUnsupportedAssetIsNotQueried() {
    SectionContext context = physicalTableContext();
    when(summaryReader.read(7L, "sales", "public", "orders"))
        .thenReturn(new QualityAssetSectionSummary(false, null, 0, 0, null));

    var result = provider.query(context);
    var unsupported = new SectionContext("modeling:model:9", "MODEL", "9", Map.of());

    assertThat(result.status()).isEqualTo(SectionStatus.EMPTY);
    assertThat(result.reason()).contains("尚未纳入质量管理");
    assertThat(provider.supports(unsupported)).isFalse();
  }

  @Test
  void incompletePhysicalCoordinateIsNotSupported() {
    var incomplete = new SectionContext("metadata:table:42", "METADATA", "42",
        Map.of("dataSourceId", "7", "tableName", "orders"));

    assertThat(provider.supports(incomplete)).isFalse();
  }

  @Test
  void waitingExecutionIsPresentedAsCurrentEvidenceAndLinksToItsWorkspace() {
    when(summaryReader.read(7L, "sales", "public", "orders"))
        .thenReturn(new QualityAssetSectionSummary(true, 9L, 1, 1,
            new QualityAssetSectionSummary.LatestExecution(
                "QX-2", "WAITING", "RUNNING", 0,
                LocalDateTime.parse("2026-09-24T10:00:00"), null)));

    var result = provider.query(physicalTableContext());
    var values = ((SectionMapSummary) result.summary()).values();

    assertThat(result.status()).isEqualTo(SectionStatus.OK);
    assertThat(values.get("latestExecution"))
        .isEqualTo(new QualityAssetSectionSummary.LatestExecution(
            "QX-2", "WAITING", "RUNNING", 0,
            LocalDateTime.parse("2026-09-24T10:00:00"), null));
    assertThat(result.actions().get(1).target()).isEqualTo("/data-quality/execution/QX-2");
  }

  private static SectionContext physicalTableContext() {
    return new SectionContext("metadata:table:42", "METADATA", "42",
        Map.of("dataSourceId", "7", "databaseName", "sales",
            "schemaName", "public", "tableName", "orders"));
  }
}
