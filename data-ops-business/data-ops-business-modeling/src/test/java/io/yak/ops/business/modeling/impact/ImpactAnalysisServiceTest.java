package io.yak.ops.business.modeling.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.common.bean.po.modeling.ModelingColumnMappingPO;
import io.yak.ops.common.bean.po.modeling.ModelingLayerFieldMappingPO;
import io.yak.ops.business.modeling.repository.LayerFieldMappingRepository;
import io.yak.ops.business.modeling.repository.MappingRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 影响分析单元测试:标准字段维度与来源列维度。 */
class ImpactAnalysisServiceTest {

  private LayerFieldMappingRepository layerFieldMappingRepository;
  private MappingRepository mappingRepository;
  private ImpactAnalysisService service;

  @BeforeEach
  void setUp() {
    layerFieldMappingRepository = mock(LayerFieldMappingRepository.class);
    mappingRepository = mock(MappingRepository.class);
    service = new ImpactAnalysisService(layerFieldMappingRepository, mappingRepository);
  }

  @Test
  void byStandardFieldReturnsAllLandings() {
    ModelingLayerFieldMappingPO landing = new ModelingLayerFieldMappingPO();
    landing.setModelId(300L);
    landing.setLayerId(2L);
    landing.setProcessFieldId(9L);
    landing.setLayerFieldName("user_phone");
    when(layerFieldMappingRepository.listByProcessField(9L)).thenReturn(List.of(landing));

    List<ImpactAnalysisService.ImpactItem> items = service.byStandardField(9L);
    assertEquals(1, items.size());
    assertEquals(300L, items.get(0).modelId());
    assertEquals("STANDARD_FIELD", items.get(0).impactKind());
  }

  @Test
  void bySourceColumnReturnsMappedModels() {
    ModelingColumnMappingPO mapping = new ModelingColumnMappingPO();
    mapping.setModelId(300L);
    mapping.setTargetColumn("user_id");
    mapping.setSourceDatasourceId(10L);
    mapping.setSourceDatabase("shop");
    mapping.setSourceTable("ods_user");
    mapping.setSourceColumn("id");
    when(mappingRepository.listBySource(10L, "shop", "ods_user", "id"))
        .thenReturn(List.of(mapping));

    List<ImpactAnalysisService.ImpactItem> items =
        service.bySourceColumn(10L, "shop", "ods_user", "id");
    assertEquals(1, items.size());
    assertEquals("SOURCE_COLUMN", items.get(0).impactKind());
    assertEquals("user_id", items.get(0).targetColumn());
  }
}
