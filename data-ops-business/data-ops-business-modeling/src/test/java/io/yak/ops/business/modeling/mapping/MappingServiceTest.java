package io.yak.ops.business.modeling.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.MappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureRepository;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 来源映射规则单元测试:目标列校验、源列存在校验、表达式语法、未映射标识。 */
class MappingServiceTest {

  private MappingRepository mappingRepository;
  private ModelRepository modelRepository;
  private ModelStructureRepository structureRepository;
  private DataSourceCatalogReader catalogReader;
  private MappingService service;

  @BeforeEach
  void setUp() {
    mappingRepository = mock(MappingRepository.class);
    modelRepository = mock(ModelRepository.class);
    structureRepository = mock(ModelStructureRepository.class);
    catalogReader = mock(DataSourceCatalogReader.class);
    when(modelRepository.findById(1L))
        .thenReturn(Optional.of(new io.yak.ops.business.modeling.domain.Model(
            1L, "m1", "订单", io.yak.ops.business.modeling.domain.ModelDialect.MYSQL, null,
            io.yak.ops.business.modeling.domain.ModelStatus.DRAFT, null, null, null, null, null,
            null, null, null, null)));
    when(structureRepository.findColumns(1L))
        .thenReturn(
            List.of(
                new ColumnDefinition(1L, "user_id", "BIGINT", null, null, false, null, null, null,
                    0),
                new ColumnDefinition(2L, "user_name", "VARCHAR", 128, null, true, null, null, null,
                    1)));
    service =
        new MappingService(mappingRepository, modelRepository, structureRepository, catalogReader);
  }

  @Test
  void listMarksUnmappedColumns() {
    when(mappingRepository.listByModel(1L))
        .thenReturn(
            List.of(mapping("user_id", "shop", "ods_user", "id", null)));
    List<MappingService.MappingView> views = service.list(1L);
    assertEquals(2, views.size());
    assertEquals(true, views.get(0).mapped());
    assertEquals(false, views.get(1).mapped());
    assertEquals("BIGINT", views.get(0).dataType());
  }

  @Test
  void setMappingRejectsUnknownTargetColumn() {
    ModelingException exception =
        assertThrows(
            ModelingException.class,
            () ->
                service.setMapping(
                    1L, "not_exist", 10L, "shop", "ods_user", "id", null, "tester"));
    assertEquals(ModelingErrorCode.INVALID_COLUMN, exception.getErrorCode());
    verify(mappingRepository, never()).upsert(any(), any());
  }

  @Test
  void setMappingRejectsMissingSourceColumn() {
    when(catalogReader.listColumns(10L, "shop", null, "ods_user"))
        .thenReturn(List.of(new CatalogColumn("id", "BIGINT", -5, 0, 0, false, 1, true, null)));
    ModelingException exception =
        assertThrows(
            ModelingException.class,
            () ->
                service.setMapping(
                    1L, "user_id", 10L, "shop", "ods_user", "ghost", null, "tester"));
    assertEquals(ModelingErrorCode.INVALID_COLUMN, exception.getErrorCode());
  }

  @Test
  void setMappingRejectsInvalidExpression() {
    when(catalogReader.listColumns(10L, "shop", null, "ods_user"))
        .thenReturn(List.of(new CatalogColumn("id", "BIGINT", -5, 0, 0, false, 1, true, null)));
    ModelingException exception =
        assertThrows(
            ModelingException.class,
            () ->
                service.setMapping(
                    1L, "user_id", 10L, "shop", "ods_user", "id", "SELECT 1", "tester"));
    assertEquals(ModelingErrorCode.INVALID_COLUMN, exception.getErrorCode());
  }

  @Test
  void setMappingUpsertsWhenAllValid() {
    when(catalogReader.listColumns(10L, "shop", null, "ods_user"))
        .thenReturn(List.of(new CatalogColumn("id", "BIGINT", -5, 0, 0, false, 1, true, null)));
    when(mappingRepository.findByTargetColumn(1L, "user_id")).thenReturn(Optional.empty());
    when(mappingRepository.upsert(any(), eq("tester")))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.setMapping(1L, "user_id", 10L, "shop", "ods_user", "id", "CAST(id AS CHAR)", "tester");

    var captor = org.mockito.ArgumentCaptor.forClass(ModelingColumnMappingPO.class);
    verify(mappingRepository).upsert(captor.capture(), eq("tester"));
    assertEquals("CAST(id AS CHAR)", captor.getValue().getTransformExpr());
  }

  @Test
  void expressionValidatorChecksSyntax() {
    assertNull(TransformExpressionValidator.validate(null));
    assertNull(TransformExpressionValidator.validate("CAST(amount AS DECIMAL(18,2))"));
    assertEquals(
        "括号不配平：缺少右括号",
        TransformExpressionValidator.validate("CONCAT(a, b"));
    assertEquals(
        "表达式禁止包含 SELECT",
        TransformExpressionValidator.validate("SELECT 1"));
  }

  private ModelingColumnMappingPO mapping(
      String target, String database, String table, String column, String expr) {
    ModelingColumnMappingPO po = new ModelingColumnMappingPO();
    po.setModelId(1L);
    po.setTargetColumn(target);
    po.setSourceDatasourceId(10L);
    po.setSourceDatabase(database);
    po.setSourceTable(table);
    po.setSourceColumn(column);
    po.setTransformExpr(expr);
    return po;
  }
}
