package io.yak.ops.business.home.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetOverviewSnapshot;
import io.yak.ops.business.dataset.DatasetService;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.query.LineageQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class HomeAssetOverviewReaderTest {

  @Test
  void shouldAggregateDatasetAndLineageOverviewFromBoundedReadServices() {
    DatasetService datasetService = mock(DatasetService.class);
    LineageQueryService lineageQueryService = mock(LineageQueryService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<DatasetService> datasetProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<LineageQueryService> lineageProvider = mock(ObjectProvider.class);
    when(datasetProvider.getIfAvailable()).thenReturn(datasetService);
    when(lineageProvider.getIfAvailable()).thenReturn(lineageQueryService);

    ZoneId zone = ZoneId.systemDefault();
    Instant todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
    Instant recentTime = todayStart.plusSeconds(3_600);
    Dataset online =
        new Dataset(
            2L,
            "订单分析数据集",
            "订单主题分析",
            DatasetStatus.ONLINE,
            22L,
            recentTime,
            recentTime.plusSeconds(60));
    Dataset offline =
        new Dataset(
            1L,
            "客户画像数据集",
            null,
            DatasetStatus.OFFLINE,
            11L,
            todayStart.minusSeconds(86_400),
            todayStart.minusSeconds(120));
    when(datasetService.overview(any(), any(), eq(5)))
        .thenReturn(new DatasetOverviewSnapshot(2L, 1L, List.of(online, offline), List.of(online)));

    LineageAsset source = asset(11L, "ods_order", LineageAssetType.TABLE, recentTime);
    LineageAsset target = asset(12L, "dwd_order_detail", LineageAssetType.TABLE, recentTime);
    LineageRelation relation = relation(21L, source.id(), target.id(), recentTime);
    when(lineageQueryService.overview(any(), any(), eq(6)))
        .thenReturn(
            new LineageQueryService.Overview(
                42L,
                9L,
                5L,
                8L,
                20L,
                3L,
                List.of(source, target),
                List.of(relation)));

    HomeAssetOverviewReader.OverviewResponse response =
        new HomeAssetOverviewReader(datasetProvider, lineageProvider).overview();

    assertThat(response.dataset().datasetCount()).isEqualTo(2L);
    assertThat(response.dataset().tableAssetCount()).isEqualTo(8L);
    assertThat(response.dataset().columnAssetCount()).isEqualTo(20L);
    assertThat(response.dataset().todayCreatedCount()).isEqualTo(1L);
    assertThat(response.dataset().recentDatasets())
        .extracting(HomeAssetOverviewReader.DatasetItem::name)
        .containsExactly("订单分析数据集", "客户画像数据集");
    assertThat(response.dataset().onlineDatasets())
        .extracting(HomeAssetOverviewReader.DatasetItem::name)
        .containsExactly("订单分析数据集");

    assertThat(response.lineage().assetCount()).isEqualTo(42L);
    assertThat(response.lineage().relationCount()).isEqualTo(9L);
    assertThat(response.lineage().todayUpdatedCount()).isEqualTo(5L);
    assertThat(response.lineage().datasetAssetCount()).isEqualTo(3L);
    assertThat(response.lineage().nodes())
        .extracting(HomeAssetOverviewReader.LineageNode::name)
        .containsExactly("ods_order", "dwd_order_detail");
    assertThat(response.lineage().edges()).hasSize(1);
    assertThat(response.lineage().recentActivities())
        .singleElement()
        .satisfies(
            activity -> {
              assertThat(activity.sourceName()).isEqualTo("ods_order");
              assertThat(activity.targetName()).isEqualTo("dwd_order_detail");
              assertThat(activity.relationType()).isEqualTo("DERIVES_FROM");
            });
    verify(datasetService, never()).list();
  }

  @Test
  void shouldExposeUnavailableMetricsWithoutInventingZeroes() {
    @SuppressWarnings("unchecked")
    ObjectProvider<DatasetService> datasetProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<LineageQueryService> lineageProvider = mock(ObjectProvider.class);
    when(datasetProvider.getIfAvailable()).thenReturn(null);
    when(lineageProvider.getIfAvailable()).thenReturn(null);

    HomeAssetOverviewReader.OverviewResponse response =
        new HomeAssetOverviewReader(datasetProvider, lineageProvider).overview();

    assertThat(response.dataset().datasetCount()).isNull();
    assertThat(response.dataset().tableAssetCount()).isNull();
    assertThat(response.dataset().columnAssetCount()).isNull();
    assertThat(response.dataset().todayCreatedCount()).isNull();
    assertThat(response.dataset().recentDatasets()).isEmpty();
    assertThat(response.dataset().onlineDatasets()).isEmpty();
    assertThat(response.lineage().assetCount()).isNull();
    assertThat(response.lineage().relationCount()).isNull();
    assertThat(response.lineage().todayUpdatedCount()).isNull();
    assertThat(response.lineage().datasetAssetCount()).isNull();
    assertThat(response.lineage().nodes()).isEmpty();
    assertThat(response.lineage().edges()).isEmpty();
    assertThat(response.lineage().recentActivities()).isEmpty();
  }

  @Test
  void shouldKeepLineageAvailableWhenDatasetQueryFails() {
    DatasetService datasetService = mock(DatasetService.class);
    LineageQueryService lineageQueryService = mock(LineageQueryService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<DatasetService> datasetProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<LineageQueryService> lineageProvider = mock(ObjectProvider.class);
    when(datasetProvider.getIfAvailable()).thenReturn(datasetService);
    when(lineageProvider.getIfAvailable()).thenReturn(lineageQueryService);
    when(datasetService.overview(any(), any(), eq(5)))
        .thenThrow(new IllegalStateException("dataset unavailable"));
    when(lineageQueryService.overview(any(), any(), eq(6)))
        .thenReturn(
            new LineageQueryService.Overview(
                1L, 0L, 0L, 1L, 0L, 0L, List.of(), List.of()));

    HomeAssetOverviewReader.OverviewResponse response =
        new HomeAssetOverviewReader(datasetProvider, lineageProvider).overview();

    assertThat(response.dataset().datasetCount()).isNull();
    assertThat(response.dataset().tableAssetCount()).isEqualTo(1L);
    assertThat(response.lineage().assetCount()).isEqualTo(1L);
  }

  private LineageAsset asset(
      long id, String name, LineageAssetType assetType, Instant updatedAt) {
    return new LineageAsset(
        id,
        assetType.name().toLowerCase() + ":" + name,
        assetType,
        name,
        "TEST",
        String.valueOf(id),
        null,
        null,
        null,
        null,
        assetType == LineageAssetType.TABLE ? name : null,
        assetType == LineageAssetType.COLUMN ? name : null,
        null,
        updatedAt.minusSeconds(60),
        updatedAt);
  }

  private LineageRelation relation(
      long id, long sourceAssetId, long targetAssetId, Instant occurredAt) {
    return new LineageRelation(
        id,
        sourceAssetId,
        targetAssetId,
        LineageRelationType.DERIVES_FROM,
        "TEST",
        "case",
        null,
        BigDecimal.ONE,
        "v1",
        occurredAt,
        null,
        occurredAt.minusSeconds(60),
        occurredAt);
  }
}
