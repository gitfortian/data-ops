package io.yak.ops.business.analysis.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.yak.ops.business.analysis.dao.mapper.AnalysisMapper;
import io.yak.ops.business.analysis.dao.model.AnalysisPO;
import io.yak.ops.business.analysis.lineage.AnalysisLineageSynchronizer;
import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** CHART provider 契约:asset_key=chart:analysis:{id} 与血缘登记键同源(D6);内嵌小图不入台账。 */
class ChartAssetProviderTest {

  private AnalysisMapper mapper;
  private ChartAssetProvider provider;

  @BeforeEach
  void setUp() {
    mapper = Mockito.mock(AnalysisMapper.class);
    provider = new ChartAssetProvider(mapper);
  }

  @Test
  void assetKeyMatchesLineageRegistrationKeyExactly() {
    when(mapper.selectList(any())).thenReturn(List.of(analysis(9L)));
    AssetDescriptor item =
        provider.cursorList(new AssetCursorQuery(1L, null, null, 10)).items().get(0);
    assertEquals("chart:analysis:9", item.assetKey());
    assertEquals(AnalysisLineageSynchronizer.chartAssetKey(9L), item.assetKey());
    assertEquals("9", item.sourceId());
    assertEquals(AssetSourceType.CHART, provider.sourceType());
  }

  @Test
  void descriptorMapsAnalysisFields() {
    when(mapper.selectList(any())).thenReturn(List.of(analysis(9L)));
    AssetDescriptor item =
        provider.cursorList(new AssetCursorQuery(1L, null, null, 10)).items().get(0);
    assertEquals("渠道趋势图", item.name());
    assertEquals(AssetType.CHART, item.assetType());
    assertNull(item.suggestedOwner());
    assertEquals("LINE", item.extra().get("chartType"));
    assertEquals("21", item.extra().get("datasetId"));
    assertEquals(64, item.contentHash().length());
  }

  @Test
  void cursorAndRefreshSemantics() {
    when(mapper.selectList(any())).thenReturn(List.of(analysis(1L)));
    assertNull(provider.cursorList(new AssetCursorQuery(1L, null, null, 5)).nextCursor());
    when(mapper.selectById(4L)).thenReturn(null);
    assertEquals(Optional.empty(), provider.refresh("4"));
    assertEquals(Optional.empty(), provider.refresh("x"));
    when(mapper.selectById(5L)).thenReturn(analysis(5L));
    assertEquals("chart:analysis:5", provider.refresh("5").orElseThrow().assetKey());
  }

  private static AnalysisPO analysis(Long id) {
    AnalysisPO po = new AnalysisPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setName("渠道趋势图");
    po.setDescription("描述");
    po.setDatasetId(21L);
    po.setChartType("LINE");
    po.setUpdateTime(Timestamp.valueOf(LocalDateTime.now()));
    return po;
  }
}
