package io.yak.ops.business.modeling.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.api.ModelingImportApi;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.governance.StandardFieldMatcher;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.semantic.api.LayerConfigApi;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.StandardRecommendApi;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 逆向导入单元测试:编码命中跳过/补字段、列映射与标准字段关联、来源标记、
 * 单表失败不中断、目标分层落库。
 */
class ReverseImportServiceTest {

  private DataSourceCatalogReader catalogReader;
  private ModelStructureService structureService;
  private ModelRepository modelRepository;
  private LayerConfigApi layerConfigApi;
  private ProcessApi processApi;
  private StandardQueryApi standardQueryApi;
  private StandardRecommendApi recommendApi;
  private ReverseImportWriter writer;
  private ReverseImportService service;

  @BeforeEach
  void setUp() {
    catalogReader = mock(DataSourceCatalogReader.class);
    structureService = mock(ModelStructureService.class);
    modelRepository = mock(ModelRepository.class);
    layerConfigApi = mock(LayerConfigApi.class);
    processApi = mock(ProcessApi.class);
    standardQueryApi = mock(StandardQueryApi.class);
    recommendApi = mock(StandardRecommendApi.class);
    writer = mock(ReverseImportWriter.class);
    when(layerConfigApi.resolveByCode("ODS"))
        .thenReturn(
            new WarehouseLayer(9L, "ODS", "贴源层", "yak_ods", 5L, null, null, null, null, null, 10,
                "ENABLED", false, false, null, null, null));
    when(processApi.listFields(null))
        .thenReturn(
            List.of(
                new StandardField(9L, "order_id", "订单ID", "PROCESS", StandardField.STATUS_ENABLED,
                    "VARCHAR", 190L, null, null, null, null, "订单主键",
                    StandardField.SOURCE_MANUAL, 1, true, null, null, null)));
    // 53(用户裁定修订):类型标准来自推荐引擎(关键词匹配);审阅展示用标准名解析。
    when(recommendApi.recommend(any()))
        .thenReturn(
            new StandardRecommendApi.RecommendationReport(
                new StandardRecommendApi.NamingCheck(true, true, 1L, "field_snake", "字段规范", null),
                List.of(new StandardRecommendApi.StandardCandidate(2L, "TYPE", "amount", "金额",
                    null, null, null)),
                List.of(), List.of(), List.of(), List.of()));
    when(standardQueryApi.labels(any()))
        .thenReturn(Map.of(2L, "金额（amount）", 190L, "标识（id）"));
    service =
        new ReverseImportService(catalogReader, structureService, modelRepository,
            layerConfigApi, processApi, new StandardFieldMatcher(), standardQueryApi,
            recommendApi, writer);
  }

  @Test
  void importCreatesModelsForNewTables() {
    when(modelRepository.findByCode("ods_user")).thenReturn(Optional.empty());
    when(catalogReader.listColumns(eq(10L), eq("shop"), isNull(), eq("ods_user")))
        .thenReturn(
            List.of(
                new CatalogColumn("order_id", "VARCHAR", 12, 32, 0, false, 1, true, "订单ID"),
                new CatalogColumn("user_name", "VARCHAR", 12, 128, 0, true, 2, false, "用户名")));
    when(writer.createWithStructure(any(), eq("tester")))
        .thenReturn(model(77L, "ods_user"));

    ModelingImportApi.ImportResult result =
        service.importTables(
            new ModelingImportApi.ImportRequest(
                "MYSQL", null, "ODS",
                List.of(new ModelingImportApi.ImportItem(10L, "shop", "ods_user", null, "用户表",
                    "用户表", null))),
            "tester");

    assertEquals(List.of("ods_user"), result.created());
    assertTrue(result.filled().isEmpty());
    assertTrue(result.failed().isEmpty());
    assertEquals(
        ModelingImportApi.ImportResult.ImportAction.CREATED, result.models().get(0).action());
    assertEquals(77L, result.models().get(0).modelId());

    ArgumentCaptor<ReverseImportPlan> plan = ArgumentCaptor.forClass(ReverseImportPlan.class);
    verify(writer).createWithStructure(plan.capture(), eq("tester"));
    // 源表列 + 技术字段 process_time/event_time
    assertEquals(4, plan.getValue().columns().size());
    assertEquals(List.of("order_id"), plan.getValue().primaryKey());
    assertEquals("ods_user", plan.getValue().tableName());
    // 目标分层落库(38)+ 来源标记(08 缺口补齐)
    assertEquals("ODS", plan.getValue().layerCode());
    assertEquals(10L, plan.getValue().sourceDatasourceId());
    assertEquals("shop", plan.getValue().sourceDatabase());
    assertEquals("ods_user", plan.getValue().sourceTable());
    // 真正源列映射集合不包含自动生成的 process_time/event_time。
    assertEquals(List.of("order_id", "user_name"), plan.getValue().sourceColumnNames());
    // 标准字段关联写在列上(38 治理;order_id 精确命中)
    assertEquals(9L, plan.getValue().columns().get(0).stdFieldId());
    assertEquals(null, plan.getValue().columns().get(1).stdFieldId());
    // 53(用户裁定修订):类型标准来自推荐引擎关键词匹配(order_id/user_name 都按推荐取 amount);
    // 标准字段关联仍绑定驱动。
    assertEquals(2L, plan.getValue().columns().get(0).stdTypeId());
    assertEquals(2L, plan.getValue().columns().get(1).stdTypeId());
    ModelingImportApi.ImportResult.ColumnMatch orderIdMatch =
        result.models().get(0).fields().get(0);
    assertEquals(2L, orderIdMatch.stdTypeId());
    assertEquals("金额（amount）", orderIdMatch.stdTypeName());
    // 38/53:类型标准按推荐引擎套用;安全标注来自标准字段绑定(本夹具无);命名在 ODS 不套用。
    assertEquals(2, result.standardApply().typeApplied());
    assertEquals(0, result.standardApply().securityApplied());
    assertEquals(0, result.standardApply().namingApplied());
    assertEquals(0, result.standardApply().degraded());
    verify(writer, never()).fillStructure(any(), any(), any());
  }

  @Test
  void importReportsPerColumnMatchDetail() {
    when(modelRepository.findByCode("ods_user")).thenReturn(Optional.empty());
    when(catalogReader.listColumns(eq(10L), eq("shop"), isNull(), eq("ods_user")))
        .thenReturn(
            List.of(
                new CatalogColumn("order_id", "VARCHAR", 12, 32, 0, false, 1, true, "订单ID"),
                new CatalogColumn("order_no", "VARCHAR", 12, 64, 0, true, 2, false, "订单号"),
                new CatalogColumn("user_name", "VARCHAR", 12, 128, 0, true, 3, false, "用户名")));
    when(writer.createWithStructure(any(), eq("tester"))).thenReturn(model(77L, "ods_user"));

    ModelingImportApi.ImportResult result =
        service.importTables(
            new ModelingImportApi.ImportRequest(
                "MYSQL", null, "ODS",
                List.of(new ModelingImportApi.ImportItem(10L, "shop", "ods_user", null, null, null, null))),
            "tester");

    List<ModelingImportApi.ImportResult.ColumnMatch> fields = result.models().get(0).fields();
    assertEquals(5, fields.size());
    // 名称精确 → 直接落关联
    ModelingImportApi.ImportResult.ColumnMatch matched = fields.get(0);
    assertEquals("order_id", matched.columnName());
    assertEquals(9L, matched.stdFieldId());
    assertEquals("order_id", matched.stdFieldCode());
    assertEquals(StandardFieldMatcher.BY_EXACT, matched.matchedBy());
    assertNull(matched.suggestedStdFieldId());
    assertTrue(!matched.technical());
    // 名称相似(order_no ↔ order_id 相似度 0.71)→ 只给建议,不落关联
    ModelingImportApi.ImportResult.ColumnMatch fuzzy = fields.get(1);
    assertEquals("order_no", fuzzy.columnName());
    assertNull(fuzzy.stdFieldId());
    assertEquals(StandardFieldMatcher.BY_FUZZY, fuzzy.matchedBy());
    assertEquals(9L, fuzzy.suggestedStdFieldId());
    assertEquals("订单ID", fuzzy.suggestedStdFieldName());
    // 未命中
    ModelingImportApi.ImportResult.ColumnMatch unmatched = fields.get(2);
    assertNull(unmatched.stdFieldId());
    assertNull(unmatched.matchedBy());
    assertTrue(!unmatched.technical());
    // 技术列不参与治理
    ModelingImportApi.ImportResult.ColumnMatch technical = fields.get(3);
    assertTrue(technical.technical());
    assertNull(technical.stdFieldId());
    assertEquals("process_time", technical.columnName());
  }

  @Test
  void reimportFillsExistingModelWithoutColumns() {
    when(modelRepository.findByCode("ods_user"))
        .thenReturn(Optional.of(model(77L, "ods_user")));
    when(catalogReader.listColumns(eq(10L), eq("shop"), isNull(), eq("ods_user")))
        .thenReturn(List.of(new CatalogColumn("id", "BIGINT", -5, 0, 0, false, 1, true, null)));
    // 已有模型但一个字段都没有(历史半成品):应补字段而不是跳过。
    when(structureService.get(77L)).thenReturn(structure(77L, "ods_user", null, List.of()));

    ModelingImportApi.ImportResult result =
        service.importTables(
            new ModelingImportApi.ImportRequest(
                "MYSQL", null, "ODS",
                List.of(new ModelingImportApi.ImportItem(10L, "shop", "ods_user", null, null, null, null))),
            "tester");

    assertEquals(List.of("ods_user"), result.filled());
    assertTrue(result.created().isEmpty());
    assertTrue(result.skipped().isEmpty());
    assertEquals(
        ModelingImportApi.ImportResult.ImportAction.FILLED, result.models().get(0).action());
    verify(writer).fillStructure(eq(77L), any(), eq("tester"));
    verify(writer, never()).createWithStructure(any(), any());
  }

  @Test
  void reimportKeepsExistingTableNameLayerAndSource() {
    when(modelRepository.findByCode("ods_user"))
        .thenReturn(Optional.of(modelWithLayerAndSource(77L, "ods_user", "DWD", 10L)
            .withSource(10L, "shop", "ods_user")));
    when(catalogReader.listColumns(eq(10L), eq("shop"), isNull(), eq("ods_user")))
        .thenReturn(List.of(new CatalogColumn("id", "BIGINT", -5, 0, 0, false, 1, true, null)));
    when(structureService.get(77L))
        .thenReturn(structure(77L, "ods_user", "ods_user_landing", List.of()));

    service.importTables(
        new ModelingImportApi.ImportRequest(
            "MYSQL", null, "ODS",
            List.of(new ModelingImportApi.ImportItem(10L, "shop", "ods_user", null, null, null, null))),
        "tester");

    ArgumentCaptor<ReverseImportPlan> plan = ArgumentCaptor.forClass(ReverseImportPlan.class);
    verify(writer).fillStructure(eq(77L), plan.capture(), eq("tester"));
    assertEquals("ods_user_landing", plan.getValue().tableName());
    // 已有分层不覆盖；传真实源身份给 writer 校验后仅补齐列映射。
    assertNull(plan.getValue().layerCode());
    assertEquals(10L, plan.getValue().sourceDatasourceId());
    assertEquals(List.of("id"), plan.getValue().sourceColumnNames());
  }

  @Test
  void reimportRejectsCodeCollisionBoundToDifferentSource() {
    when(modelRepository.findByCode("ods_user"))
        .thenReturn(Optional.of(modelWithLayerAndSource(77L, "ods_user", "ODS", 3L)
            .withSource(3L, "shop", "another_table")));
    when(catalogReader.listColumns(eq(10L), eq("shop"), isNull(), eq("ods_user")))
        .thenReturn(List.of(new CatalogColumn("id", "BIGINT", -5, 0, 0, false, 1, true, null)));

    ModelingImportApi.ImportResult result =
        service.importTables(
            new ModelingImportApi.ImportRequest(
                "MYSQL", null, "ODS",
                List.of(new ModelingImportApi.ImportItem(10L, "shop", "ods_user",
                    null, null, null, null))),
            "tester");

    assertEquals(1, result.failed().size());
    assertTrue(result.failed().get(0).reason().contains("不同来源"));
    verify(writer, never()).fillStructure(any(), any(), any());
    verify(writer, never()).createWithStructure(any(), any());
    verify(modelRepository, never()).assignSource(any(), any(), any(), any(), any());
  }

  @Test
  void reimportSkipsModelThatAlreadyHasColumns() {
    when(modelRepository.findByCode("ods_user"))
        .thenReturn(Optional.of(model(77L, "ods_user")));
    when(catalogReader.listColumns(eq(10L), eq("shop"), isNull(), eq("ods_user")))
        .thenReturn(List.of(new CatalogColumn("id", "BIGINT", -5, 0, 0, false, 1, true, null)));
    when(structureService.get(77L))
        .thenReturn(
            structure(77L, "ods_user", "ods_user",
                List.of(new ColumnDefinition(1L, "id", "BIGINT", null, null, false, null, null,
                    null, 0, null, null, null, null, null, null))));

    ModelingImportApi.ImportResult result =
        service.importTables(
            new ModelingImportApi.ImportRequest(
                "MYSQL", null, "ODS",
                List.of(new ModelingImportApi.ImportItem(10L, "shop", "ods_user", null, null, null, null))),
            "tester");

    assertEquals(List.of("ods_user"), result.skipped());
    assertEquals(
        ModelingImportApi.ImportResult.ImportAction.SKIPPED, result.models().get(0).action());
    verify(writer, never()).fillStructure(any(), any(), any());
    verify(writer, never()).createWithStructure(any(), any());
    // Skipped models are read-only, not silently bound to a guessed source.
    verify(modelRepository, never()).assignSource(any(), any(), any(), any(), any());
  }

  @Test
  void importKeepsColumnWhenNoTypeStandardMatched() {
    when(modelRepository.findByCode("ods_cust")).thenReturn(Optional.empty());
    when(catalogReader.listColumns(eq(10L), eq("shop"), isNull(), eq("ods_cust")))
        .thenReturn(
            List.of(new CatalogColumn("zzz_col", "VARCHAR", 12, 32, 0, true, 1, false, "未知列")));
    when(writer.createWithStructure(any(), eq("tester"))).thenReturn(model(78L, "ods_cust"));
    // 53(用户裁定修订):类型标准按关键词匹配;推荐引擎无候选 = 不套用,标 warning。
    when(recommendApi.recommend(any()))
        .thenReturn(
            new StandardRecommendApi.RecommendationReport(
                new StandardRecommendApi.NamingCheck(true, true, 1L, "field_snake", "字段规范", null),
                List.of(), List.of(), List.of(), List.of(), List.of()));
    ModelingImportApi.ImportResult result =
        service.importTables(
            new ModelingImportApi.ImportRequest(
                "MYSQL", null, "ODS",
                List.of(new ModelingImportApi.ImportItem(10L, "shop", "ods_cust", null, null, null, null))),
            "tester");

    assertEquals(0, result.standardApply().typeApplied());
    ModelingImportApi.ImportResult.ColumnMatch field = result.models().get(0).fields().get(0);
    assertNull(field.stdTypeId());
    assertNull(field.stdTypeName());
    // 类型标准未命中回报到降级原因,驱动用户沉淀/关联(40/56)。
    assertTrue(field.degradedReason().contains("类型标准"));
  }

  @Test
  void eventTimeAutoDetectsSourceBusinessField() {
    when(modelRepository.findByCode("ods_order")).thenReturn(Optional.empty());
    when(catalogReader.listColumns(eq(10L), eq("shop"), isNull(), eq("ods_order")))
        .thenReturn(
            List.of(
                new CatalogColumn("order_id", "VARCHAR", 12, 32, 0, false, 1, true, null),
                new CatalogColumn("order_time", "DATETIME", 12, 0, 0, true, 2, false, null)));
    when(writer.createWithStructure(any(), eq("tester"))).thenReturn(model(80L, "ods_order"));

    service.importTables(
        new ModelingImportApi.ImportRequest(
            "MYSQL", null, "ODS",
            List.of(new ModelingImportApi.ImportItem(10L, "shop", "ods_order", null, null, null, null))),
        "tester");

    ArgumentCaptor<ReverseImportPlan> plan = ArgumentCaptor.forClass(ReverseImportPlan.class);
    verify(writer).createWithStructure(plan.capture(), eq("tester"));
    // 2 源列 + process_time + event_time
    assertEquals(4, plan.getValue().columns().size());
    assertEquals("process_time", plan.getValue().columns().get(2).columnName());
    assertEquals("数据处理时间", plan.getValue().columns().get(2).comment());
    assertEquals("event_time", plan.getValue().columns().get(3).columnName());
    assertEquals("业务事件时间", plan.getValue().columns().get(3).comment());
  }

  @Test
  void eventTimeSourceAllowsOverridePerTable() {
    when(modelRepository.findByCode("ods_cust")).thenReturn(Optional.empty());
    when(catalogReader.listColumns(eq(10L), eq("shop"), isNull(), eq("ods_cust")))
        .thenReturn(
            List.of(
                new CatalogColumn("cust_id", "BIGINT", 19, 0, 0, false, 1, true, null),
                new CatalogColumn("created_date", "DATE", 12, 0, 0, true, 2, false, null),
                new CatalogColumn("updated_at", "DATETIME", 12, 0, 0, true, 3, false, null)));
    when(writer.createWithStructure(any(), eq("tester"))).thenReturn(model(81L, "ods_cust"));

    // 自动识别会优先 updated_at(含 time),这里显式指定 created_date。
    service.importTables(
        new ModelingImportApi.ImportRequest(
            "MYSQL", null, "ODS",
            List.of(new ModelingImportApi.ImportItem(
                10L, "shop", "ods_cust", null, null, null, "created_date"))),
        "tester");

    ArgumentCaptor<ReverseImportPlan> plan = ArgumentCaptor.forClass(ReverseImportPlan.class);
    verify(writer).createWithStructure(plan.capture(), eq("tester"));
    assertEquals(
        "event_time", plan.getValue().columns().get(4).columnName());
    assertEquals("业务事件时间", plan.getValue().columns().get(4).comment());
  }

  @Test
  void importReportsFailedTableWithoutBlockingOthers() {
    when(modelRepository.findByCode(any())).thenReturn(Optional.empty());
    when(catalogReader.listColumns(eq(10L), eq("shop"), isNull(), eq("ods_bad")))
        .thenThrow(new IllegalArgumentException("boom"));
    when(catalogReader.listColumns(eq(10L), eq("shop"), isNull(), eq("ods_good")))
        .thenReturn(
            List.of(new CatalogColumn("id", "BIGINT", -5, 0, 0, false, 1, true, null)));
    when(writer.createWithStructure(any(), eq("tester"))).thenReturn(model(1L, "ods_good"));

    ModelingImportApi.ImportResult result =
        service.importTables(
            new ModelingImportApi.ImportRequest(
                "MYSQL", null, "ODS",
                List.of(
                    new ModelingImportApi.ImportItem(10L, "shop", "ods_bad", null, null, null, null),
                    new ModelingImportApi.ImportItem(10L, "shop", "ods_good", null, null, null, null))),
            "tester");

    assertEquals(List.of("ods_good"), result.created());
    assertEquals(1, result.failed().size());
    assertEquals("ods_bad", result.failed().get(0).table());
    assertEquals(
        ModelingImportApi.ImportResult.ImportAction.FAILED, result.models().get(0).action());
    assertNull(result.models().get(0).modelId());
  }

  private static Model model(Long id, String code) {
    return modelWithLayerAndSource(id, code, null, null);
  }

  private static Model modelWithLayerAndSource(
      Long id, String code, String layerCode, Long sourceDatasourceId) {
    return new Model(
        id, code, "用户表", ModelDialect.MYSQL, "用户表", ModelStatus.DRAFT, "tester", null, null,
        layerCode, null, null, null, null, null, sourceDatasourceId, null, null);
  }

  private static StructureView structure(
      Long modelId, String code, String structureTableName,
      List<ColumnDefinition> columns) {
    return StructureView.of(
        model(modelId, code), structureTableName, null, columns, List.of(), List.of(),
        new StructureView.PartitionView(null, List.of(), null), java.util.Map.of());
  }
}
