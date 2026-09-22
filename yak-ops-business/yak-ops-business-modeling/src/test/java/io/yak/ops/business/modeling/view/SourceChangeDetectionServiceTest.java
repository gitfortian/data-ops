package io.yak.ops.business.modeling.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.repository.MappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureRepository;
import io.yak.ops.common.bean.po.modeling.ModelingColumnMappingPO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 源端变更检测单元测试:新增/移除/类型漂移;未登记来源的模型。 */
class SourceChangeDetectionServiceTest {

  private MappingRepository mappingRepository;
  private ModelRepository modelRepository;
  private ModelStructureRepository structureRepository;
  private DataSourceCatalogReader catalogReader;
  private SourceChangeDetectionService service;

  @BeforeEach
  void setUp() {
    mappingRepository = mock(MappingRepository.class);
    modelRepository = mock(ModelRepository.class);
    structureRepository = mock(ModelStructureRepository.class);
    catalogReader = mock(DataSourceCatalogReader.class);
    when(modelRepository.findById(1L))
        .thenReturn(
            Optional.of(new io.yak.ops.business.modeling.domain.Model(
                1L, "dwd_order", "订单", io.yak.ops.business.modeling.domain.ModelDialect.MYSQL,
                null, io.yak.ops.business.modeling.domain.ModelStatus.DRAFT, null, null, null,
                null, null, null, null, null, null)));
    when(structureRepository.findColumns(1L))
        .thenReturn(
            List.of(
                new io.yak.ops.business.modeling.domain.ColumnDefinition(
                    1L, "user_id", "BIGINT", null, null, false, null, null, null, 0)));
    ModelingColumnMappingPO mapping = new ModelingColumnMappingPO();
    mapping.setModelId(1L);
    mapping.setTargetColumn("user_id");
    mapping.setSourceDatasourceId(10L);
    mapping.setSourceDatabase("shop");
    mapping.setSourceTable("ods_user");
    mapping.setSourceColumn("id");
    when(mappingRepository.listByModel(1L)).thenReturn(List.of(mapping));
    service =
        new SourceChangeDetectionService(
            mappingRepository, modelRepository, structureRepository, catalogReader);
  }

  @Test
  void detectReportsAddedRemovedAndTypeDrift() {
    // 源表现状:id 类型 INT(漂移),新增 amount,old_col 已消失。
    when(catalogReader.listColumns(10L, "shop", null, "ods_user"))
        .thenReturn(
            List.of(
                new CatalogColumn("id", "INT", 4, 0, 0, false, 1, false, null),
                new CatalogColumn("amount", "DECIMAL", 3, 10, 2, true, 2, false, null)));

    SourceChangeDetectionService.ModelDiffReport report = service.detect(1L);

    assertEquals(true, report.sourcesRegistered());
    assertEquals(1, report.sources().size());
    assertEquals(List.of("amount"), report.sources().get(0).addedSourceColumns());
    assertEquals(1, report.sources().get(0).typeDrift().size());
    assertEquals("INT", report.sources().get(0).typeDrift().get(0).sourceType());
  }

  @Test
  void detectWithoutSourcesReturnsEmptyRegisteredReport() {
    when(mappingRepository.listByModel(1L)).thenReturn(List.of());
    SourceChangeDetectionService.ModelDiffReport report = service.detect(1L);
    assertEquals(false, report.sourcesRegistered());
    assertEquals(0, report.sources().size());
  }
}
