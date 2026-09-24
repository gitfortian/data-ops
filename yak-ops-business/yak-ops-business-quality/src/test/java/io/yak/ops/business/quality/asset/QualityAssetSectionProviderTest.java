package io.yak.ops.business.quality.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.execution.QualityExecutionReader;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionMapSummary;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QualityAssetSectionProviderTest {

  private final QualityTableAssetReader tableAssetReader = mock(QualityTableAssetReader.class);
  private final QualityMonitorReader monitorReader = mock(QualityMonitorReader.class);
  private final QualityExecutionReader executionReader = mock(QualityExecutionReader.class);
  private final QualityAssetSectionProvider provider = new QualityAssetSectionProvider(
      tableAssetReader, monitorReader, executionReader);

  @Test
  void qualityOwnedProviderReturnsMonitorProjectionForPhysicalTable() {
    SectionContext context = physicalTableContext();
    when(tableAssetReader.isRegistered(7L, "sales", "public", "orders")).thenReturn(true);
    when(monitorReader.tableSummaries(7L, "sales", "public")).thenReturn(List.of(
        new TableMonitorSummary("orders", 9L, "Orders", 2, 4, 1, null, null, null)));

    var result = provider.query(context);

    assertThat(provider.sectionType()).isEqualTo(SectionType.QUALITY);
    assertThat(provider.supports(context)).isTrue();
    assertThat(result.ownerDomain()).isEqualTo("QUALITY");
    assertThat(result.status()).isEqualTo(SectionStatus.OK);
    assertThat(((SectionMapSummary) result.summary()).values())
        .containsEntry("registered", true)
        .containsEntry("monitorCount", 2)
        .containsEntry("enabledMonitorCount", 1);
    verify(tableAssetReader).isRegistered(7L, "sales", "public", "orders");
    verify(monitorReader).tableSummaries(7L, "sales", "public");
  }

  @Test
  void confirmedAbsenceIsEmptyAndUnsupportedAssetIsNotQueried() {
    SectionContext context = physicalTableContext();
    when(tableAssetReader.isRegistered(7L, "sales", "public", "orders")).thenReturn(false);
    when(monitorReader.tableSummaries(7L, "sales", "public")).thenReturn(List.of());

    var result = provider.query(context);
    var unsupported = new SectionContext("modeling:model:9", "MODEL", "9", Map.of());

    assertThat(result.status()).isEqualTo(SectionStatus.EMPTY);
    assertThat(result.reason()).contains("尚未纳入质量管理");
    assertThat(provider.supports(unsupported)).isFalse();
  }

  private static SectionContext physicalTableContext() {
    return new SectionContext("metadata:table:42", "METADATA", "42",
        Map.of("dataSourceId", "7", "databaseName", "sales",
            "schemaName", "public", "tableName", "orders"));
  }
}
