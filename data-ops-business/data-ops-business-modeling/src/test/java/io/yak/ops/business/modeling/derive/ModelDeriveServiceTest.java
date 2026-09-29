package io.yak.ops.business.modeling.derive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.api.ModelingStructureApi;
import io.yak.ops.business.modeling.catalog.ModelCatalogService;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.governance.StandardFieldMatcher;
import io.yak.ops.business.modeling.lineage.ModelingLineageRegistrationService;
import io.yak.ops.business.modeling.mapping.MappingService;
import io.yak.ops.business.modeling.repository.LayerFieldMappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.semantic.api.LayerConfigApi;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.StandardRecommendApi;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.business.semantic.api.BusinessProcess;
import io.yak.ops.common.bean.po.modeling.ModelingLayerFieldMappingPO;
import io.yak.ops.common.api.metric.MetricQueryApi;
import io.yak.ops.common.api.metric.MetricQueryView;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 派生建模单元测试(44 定稿):按 36 角色继承 ODS 字段、冲突消解、技术列排除、
 * 43/19 映射与治理回填、类型兼容阻断、同过程同层防重。
 */
class ModelDeriveServiceTest {

  private static final long PROCESS_ID = 50L;

  private ProcessApi processApi;
  private LayerConfigApi layerConfigApi;
  private StandardQueryApi standardQueryApi;
  private StandardRecommendApi recommendApi;
  private DataSourceCatalogReader catalogReader;
  private ModelCatalogService catalogService;
  private ModelStructureService structureService;
  private io.yak.ops.business.modeling.version.ModelPublishedStructureReader structureReader;
  private ModelRepository modelRepository;
  private LayerFieldMappingRepository layerFieldMappingRepository;
  private MappingService mappingService;
  private ModelingLineageRegistrationService lineageRegistrationService;
  private ObjectProvider<MetricQueryApi> metricQueryProvider;
  private MetricQueryApi metricQueryApi;
  private ModelDeriveService service;

  @BeforeEach
  void setUp() {
    processApi = mock(ProcessApi.class);
    layerConfigApi = mock(LayerConfigApi.class);
    standardQueryApi = mock(StandardQueryApi.class);
    recommendApi = mock(StandardRecommendApi.class);
    catalogReader = mock(DataSourceCatalogReader.class);
    catalogService = mock(ModelCatalogService.class);
    structureService = mock(ModelStructureService.class);
    structureReader =
        mock(io.yak.ops.business.modeling.version.ModelPublishedStructureReader.class);
    when(structureReader.publishedStructure(any()))
        .thenAnswer(invocation -> structureService.get(invocation.getArgument(0)));
    modelRepository = mock(ModelRepository.class);
    metricQueryApi = mock(MetricQueryApi.class);
    metricQueryProvider = mock(ObjectProvider.class);
    when(metricQueryProvider.getIfAvailable()).thenReturn(metricQueryApi);
    layerFieldMappingRepository = mock(LayerFieldMappingRepository.class);
    mappingService = mock(MappingService.class);
    lineageRegistrationService = mock(ModelingLineageRegistrationService.class);

    for (String code : List.of("DWD", "DIM", "ODS", "DWS", "ADS", "CUSTOM")) {
      when(layerConfigApi.resolveByCode(code))
          .thenReturn(
              new WarehouseLayer(2L, code, code + "层", "yak_" + code.toLowerCase(), 5L, null, null,
                  null, null, null, 20, "ENABLED", true, false, null, null, null));
    }
    when(processApi.getFieldSets(PROCESS_ID))
        .thenReturn(
            List.of(
                new StandardField(9L, "order_id", "订单ID", "PROCESS",
                    StandardField.STATUS_ENABLED, "VARCHAR", 190L, null, null, null, null, "订单主键",
                    StandardField.SOURCE_MANUAL, 1, true, null, null, null),
                new StandardField(10L, "settle_channel", "结算渠道", "DIMENSION",
                    StandardField.STATUS_ENABLED, "VARCHAR", 191L, null, null, null, null, null,
                    StandardField.SOURCE_MANUAL, 1, false, null, null, null)));
    when(processApi.getField(9L)).thenReturn(field9());
    when(processApi.getField(10L)).thenReturn(field10());
    when(catalogService.create(any(), any(), any(), any(), eq("tester"), any(), any(), any())).thenReturn(createdModel());
    when(modelRepository.assignProcess(any(), eq(PROCESS_ID), eq("DWD"), any())).thenReturn(true);
    when(lineageRegistrationService.registerModel(org.mockito.ArgumentMatchers.anyLong(), eq("tester")))
        .thenReturn(new ModelingLineageRegistrationService.RegisterView(400L, 4, 4));
    when(processApi.listProcesses(null))
        .thenReturn(
            List.of(
                new BusinessProcess(
                    PROCESS_ID, "order_create", "下单", 1L, null, null, "tester", null, 0,
                    "tester", null, null)));
    service =
        new ModelDeriveService(
            processApi, layerConfigApi, standardQueryApi, recommendApi, catalogReader,
            catalogService, structureService, structureReader, modelRepository,
            layerFieldMappingRepository,
            mappingService, new StandardFieldMatcher(), new DeriveLayerPolicy(),
            new DimConventions(), lineageRegistrationService, metricQueryProvider);
  }

  @Test
  void previewGroupsFieldsByRoleAndSkipsTechnicalColumns() {
    stubSources();
    ModelDeriveService.PreviewView view = service.preview(PROCESS_ID, "DWD", "MYSQL", null, null, null, null, null, null);

    assertEquals("DWD", view.layerCode());
    assertEquals(3, view.sources().size());
    assertTrue(view.sources().get(0).ready());
    assertEquals("trade_order", view.sources().get(0).sourceTable());
    assertEquals("MAIN", view.sources().get(0).tableRole());
    assertEquals("trade_order_detail", view.sources().get(1).sourceTable());
    assertEquals("DETAIL", view.sources().get(1).tableRole());
    // D3(v2.0 59):DWD 默认宽表——DIM 维表字段默认纳入(维度退化),可前端排除成纯事实表
    assertTrue(
        view.fields().stream()
            .filter(field -> "DIM".equals(field.tableRole()))
            .allMatch(ModelDeriveService.FieldView::include));
    // 技术列:ODS 的 event_time 不继承,只按目标层规则补 process_time/event_time 两条
    assertEquals(2, view.fields().stream().filter(ModelDeriveService.FieldView::technical).count());
    assertEquals(
        "process_time",
        view.fields().stream()
            .filter(ModelDeriveService.FieldView::technical)
            .findFirst()
            .orElseThrow()
            .sourceColumn());
    // 明细表同名 order_id 被主表先到先得合并
    ModelDeriveService.FieldView merged =
        view.fields().stream()
            .filter(field -> "order_id".equals(field.sourceColumn()))
            .findFirst()
            .orElseThrow();
    assertTrue(merged.conflicting());
    assertEquals("trade_order_detail", merged.conflictWith());
    assertEquals("trade_order", merged.sourceTable());
    // 订单ID 命中标准字段(继承自 ODS 列),pay_amount 未治理
    assertEquals(9L, merged.stdFieldId());
    assertEquals("inherited", merged.matchedBy());
    assertTrue(!merged.conflicting() || merged.landingField().equals("order_id"));
    assertEquals("order_id", merged.landingField());
    assertTrue(
        view.fields().stream()
            .filter(field -> "pay_amount".equals(field.sourceColumn()))
            .findFirst()
            .orElseThrow()
            .stdFieldId()
            == null);
    // 纳入 7 个(含 2 技术列 + DIM 宽表 cust_id);治理率只按业务字段 5 个算:命中 1 → 20%
    assertEquals(7, view.totalFields());
    assertEquals(1, view.matchedFields());
    assertEquals(20, view.governanceRate());
    assertEquals(null, view.existingModel());
  }

  @Test
  void previewWarnsWhenMainRoleMissing() {
    when(processApi.listProcessSources(PROCESS_ID))
        .thenReturn(
            List.of(
                new ProcessApi.ProcessSourceView(1L, 3L, "crm_customer", "DIM", null)));
    when(processApi.getFieldSets(PROCESS_ID)).thenReturn(List.of());
    ModelDeriveService.PreviewView view = service.preview(PROCESS_ID, "DWD", "MYSQL", null, null, null, null, null, null);
    assertTrue(view.warnings().stream().anyMatch(warning -> warning.contains("主表（MAIN）")));
  }

  @Test
  void deriveInheritsFieldsWritesMappingsAndBackfillsOds() {
    stubSources();
    // ODS 列上还没有标准字段关联:本次由过程字段集匹配命中后回填
    when(structureService.get(101L)).thenReturn(odsStructure(101L, "trade_order", false, true));
    when(structureService.assignStandardField(101L, "order_id", 9L, "tester")).thenReturn(true);

    ModelDeriveService.DeriveView view =
        service.derive(deriveRequest(), "tester");

    assertEquals(300L, view.modelId());
    // 纳入字段:order_id / pay_amount + 目标技术列 process_time/event_time
    assertEquals(4, view.columnCount());
    // 业务字段 2 个(order_id 命中,pay_amount 未治理);技术列不计入治理
    assertEquals(1, view.governedCount());
    assertEquals(1, view.unmatchedCount());
    assertEquals(50, view.governanceRate());
    assertEquals(1, view.backfilledCount());

    ArgumentCaptor<ModelingStructureApi.SaveStructureRequest> saved =
        ArgumentCaptor.forClass(ModelingStructureApi.SaveStructureRequest.class);
    verify(structureService).save(eq(300L), saved.capture(), eq("tester"));
    List<ModelingStructureApi.ColumnInput> columns = saved.getValue().columns();
    assertEquals(List.of("order_id", "pay_amount", "process_time", "event_time"),
        columns.stream().map(ModelingStructureApi.ColumnInput::columnName).toList());
    ModelingStructureApi.ColumnInput orderId = columns.get(0);
    assertEquals(9L, orderId.stdFieldId());
    assertEquals("VARCHAR", orderId.dataType());
    assertEquals(32, orderId.length());
    assertEquals("订单ID", orderId.comment());
    assertEquals(190L, orderId.stdTypeId());
    // 未治理字段没有标准字段关联,也没有 43 映射
    assertEquals(null, columns.get(1).stdFieldId());

    ArgumentCaptor<ModelingLayerFieldMappingPO> mapping =
        ArgumentCaptor.forClass(ModelingLayerFieldMappingPO.class);
    verify(layerFieldMappingRepository).upsert(mapping.capture(), eq("tester"));
    assertEquals(9L, mapping.getValue().getProcessFieldId());
    assertEquals(2L, mapping.getValue().getLayerId());
    assertEquals("order_id", mapping.getValue().getLayerFieldName());
    assertEquals("trade_order.order_id", mapping.getValue().getSourceField());

    // 19 来源映射:源=ODS 模型绑定的源表/列,并携带标准字段关联
    verify(mappingService)
        .setMapping(eq(300L), eq("order_id"), eq(3L), eq("crm_db"), eq("trade_order"),
            eq("order_id"), eq(null), eq(9L), eq("tester"));
    // 治理回填:ODS 列还没有标准字段关联时写回
    verify(structureService).assignStandardField(101L, "order_id", 9L, "tester");
    verify(lineageRegistrationService).registerModel(300L, "tester");
  }

  @Test
  void deriveRejectsWhenProcessLayerAlreadyHasModel() {
    stubSources();
    when(modelRepository.modelIdsByProcess(PROCESS_ID)).thenReturn(List.of(300L));
    when(modelRepository.findById(300L)).thenReturn(Optional.of(createdModel()));

    ModelingException exception =
        assertThrows(ModelingException.class, () -> service.derive(deriveRequest(), "tester"));
    assertEquals(ModelingErrorCode.DERIVE_CONFLICT, exception.getErrorCode());
    verify(structureService, never()).save(any(), any(), any());
  }

  @Test
  void deriveBlocksIncompatibleTypeForTargetDialect() {
    when(processApi.listProcessSources(PROCESS_ID))
        .thenReturn(List.of(new ProcessApi.ProcessSourceView(1L, 3L, "trade_order", "MAIN", null)));
    when(modelRepository.listBySource(3L, "trade_order")).thenReturn(List.of(odsModel(101L)));
    when(structureService.get(101L))
        .thenReturn(
            odsStructureWith(
                101L,
                "trade_order",
                List.of(column("order_time", "DATETIME", 19, null, "下单时间", null))));
    when(processApi.getFieldSets(PROCESS_ID)).thenReturn(List.of());
    when(recommendApi.recommend(any()))
        .thenReturn(
            new StandardRecommendApi.RecommendationReport(null, List.of(), List.of(), List.of(),
                List.of(), List.of()));

    ModelDeriveService.DeriveRequest request =
        new ModelDeriveService.DeriveRequest(
            PROCESS_ID, "DWD", "dwd_trade_order", "订单派生", "POSTGRESQL", null, null, null,
            null, null, null, null, null,
            List.of(new ModelDeriveService.DerivedField(
                "trade_order", "order_time", "order_time", null, null, true, false, null, null)));

    ModelingException exception =
        assertThrows(ModelingException.class, () -> service.derive(request, "tester"));
    assertEquals(ModelingErrorCode.INVALID_COLUMN, exception.getErrorCode());
    assertTrue(exception.getUserMessage().contains("POSTGRESQL"));
  }


  @Test
  void previewReportsUpstreamLayerForSupportedLayers() {
    stubSources();
    ModelDeriveService.PreviewView dwd = service.preview(PROCESS_ID, "DWD", "MYSQL", null, null, null, null, null, null);
    assertTrue(dwd.supported());
    assertEquals("ODS", dwd.upstreamLayer());
    assertEquals(null, dwd.unsupportedReason());

    ModelDeriveService.PreviewView dim = service.preview(PROCESS_ID, "DIM", "MYSQL", null, null, null, null, null, null);
    assertTrue(dim.supported());
    assertEquals("ODS", dim.upstreamLayer());
  }

  @Test
  void previewBlocksUnsupportedLayersWithReason() {
    // ODS:由逆向导入产生,不经派生
    ModelDeriveService.PreviewView ods = service.preview(PROCESS_ID, "ODS", "MYSQL", null, null, null, null, null, null);
    assertTrue(!ods.supported());
    assertTrue(ods.unsupportedReason().contains("逆向导入"));
    assertTrue(ods.fields().isEmpty());
    assertTrue(ods.warnings().contains(ods.unsupportedReason()));
    // DWS:上游是 DWD;该过程还没有 DWD 模型时阻断(上游缺失,先派生 DWD)
    ModelDeriveService.PreviewView dws = service.preview(PROCESS_ID, "DWS", "MYSQL", null, null, null, null, null, null);
    assertTrue(!dws.supported());
    assertEquals("DWD", dws.upstreamLayer());
    assertTrue(dws.unsupportedReason().contains("还没有 DWD 模型"));
    // ADS:上游是 DWS;该过程还没有 DWS 模型时阻断
    ModelDeriveService.PreviewView ads = service.preview(PROCESS_ID, "ADS", "MYSQL", null, null, null, null, null, null);
    assertTrue(!ads.supported());
    assertEquals("DWS", ads.upstreamLayer());
    assertTrue(ads.unsupportedReason().contains("还没有 DWS 模型"));
    // 未登记的自定义分层:按不支持处理
    ModelDeriveService.PreviewView custom = service.preview(PROCESS_ID, "CUSTOM", "MYSQL", null, null, null, null, null, null);
    assertTrue(!custom.supported());
    assertTrue(custom.unsupportedReason().contains("能力矩阵"));
  }

  @Test
  void deriveRejectsUnsupportedLayer() {
    ModelDeriveService.DeriveRequest request =
        new ModelDeriveService.DeriveRequest(
            PROCESS_ID, "DWS", "dws_trade_order", "订单汇总", "MYSQL", null, null, null, null,
            null, null, null, null, null);
    ModelingException exception =
        assertThrows(ModelingException.class, () -> service.derive(request, "tester"));
    assertEquals(ModelingErrorCode.LAYER_DERIVE_UNSUPPORTED, exception.getErrorCode());
    assertTrue(exception.getUserMessage().contains("DWS"));
    verify(structureService, never()).save(any(), any(), any());
    verify(catalogService, never()).create(any(), any(), any(), any(), any(), any(), any(), any());
  }


  // ------------------------------------------------- 50 维表约定字段

  @Test
  void dimPreviewAddsSurrogateKeyOnlyForScd1() {
    stubSources();
    ModelDeriveService.PreviewView view = service.preview(PROCESS_ID, "DIM", "MYSQL", null, null, null, null, null, null);
    assertTrue(view.supported());
    // 代理键默认补,SCD1 不补生效起止
    ModelDeriveService.FieldView surrogate =
        view.fields().stream()
            .filter(ModelDeriveService.FieldView::convention)
            .findFirst()
            .orElseThrow();
    assertEquals("dim_order_create_sk", surrogate.landingField());
    assertEquals("BIGINT", surrogate.dataType());
    assertTrue(surrogate.include());
    assertEquals(1, view.conventionFields());
    // 约定列是结构字段:不进治理率分母(纳入 7 个:4 业务 + 2 技术列 + 代理键)
    assertEquals(7, view.totalFields());
    assertEquals(25, view.governanceRate());
  }

  @Test
  void dimPreviewAddsScdColumnsForScd2() {
    stubSources();
    ModelDeriveService.PreviewView view = service.preview(PROCESS_ID, "DIM", "MYSQL", "SCD2", null, null, null, null, null);
    List<String> conventionNames =
        view.fields().stream()
            .filter(ModelDeriveService.FieldView::convention)
            .map(ModelDeriveService.FieldView::landingField)
            .toList();
    assertEquals(
        List.of("dim_order_create_sk", "start_time", "end_time", "is_current"), conventionNames);
    assertEquals(4, view.conventionFields());
    // 治理率仍只按业务字段算(纳入 10 个:4 业务 + 2 技术列 + 4 约定列)
    assertEquals(10, view.totalFields());
    assertEquals(25, view.governanceRate());
  }

  @Test
  void dimConventionsFollowTargetDialect() {
    stubSources();
    // PostgreSQL 类型目录里没有 DATETIME/BOOLEAN 之外的问题:DATETIME 不存在,应回退 TIMESTAMP
    ModelDeriveService.PreviewView view = service.preview(PROCESS_ID, "DIM", "POSTGRESQL", "SCD2", null, null, null, null, null);
    List<String> types =
        view.fields().stream()
            .filter(ModelDeriveService.FieldView::convention)
            .map(ModelDeriveService.FieldView::dataType)
            .toList();
    assertEquals(List.of("BIGINT", "TIMESTAMP", "TIMESTAMP", "BOOLEAN"), types);
  }

  @Test
  void dwdIgnoresScdTypeAndAddsNoConventions() {
    stubSources();
    ModelDeriveService.PreviewView view = service.preview(PROCESS_ID, "DWD", "MYSQL", "SCD2", null, null, null, null, null);
    assertEquals(0, view.conventionFields());
    assertTrue(view.fields().stream().noneMatch(ModelDeriveService.FieldView::convention));
  }

  @Test
  void dimDeriveSetsSurrogateKeyAsPrimaryKeyAndSkipsConventionMappings() {
    stubSources();
    when(modelRepository.findById(101L)).thenReturn(Optional.of(odsModel(101L)));
    // 前端行为:按 preview 的纳入清单回传(约定列随请求回传,不是后端硬塞)
    ModelDeriveService.PreviewView pv = service.preview(PROCESS_ID, "DIM", "MYSQL", "SCD2", null, null, null, null, null);
    List<ModelDeriveService.DerivedField> chosen =
        pv.fields().stream()
            .filter(ModelDeriveService.FieldView::include)
            .map(
                field ->
                    new ModelDeriveService.DerivedField(
                        field.sourceTable(), field.sourceColumn(), field.landingField(),
                        field.stdFieldId(), null, true, field.technical(), null, null))
            .toList();
    ModelDeriveService.DeriveRequest request =
        new ModelDeriveService.DeriveRequest(
            PROCESS_ID, "DIM", "dim_order_create", "订单维表", "MYSQL", null, null, "SCD2",
            null, null, null, null, null, chosen);

    ModelDeriveService.DeriveView view = service.derive(request, "tester");

    ArgumentCaptor<ModelingStructureApi.SaveStructureRequest> saved =
        ArgumentCaptor.forClass(ModelingStructureApi.SaveStructureRequest.class);
    verify(structureService).save(eq(300L), saved.capture(), eq("tester"));
    List<String> columnNames =
        saved.getValue().columns().stream()
            .map(ModelingStructureApi.ColumnInput::columnName)
            .toList();
    assertTrue(columnNames.contains("dim_order_create_sk"));
    assertTrue(columnNames.contains("start_time"));
    assertTrue(columnNames.contains("end_time"));
    assertTrue(columnNames.contains("is_current"));
    // 主键 = 代理键
    assertEquals(List.of("dim_order_create_sk"), saved.getValue().primaryKey());
    // 治理统计只算业务字段:主表+明细共 4 个业务字段,命中 order_id 1 个 → 25%
    assertEquals(4, view.governedCount() + view.unmatchedCount());
    assertEquals(25, view.governanceRate());
    // 约定列不写 43 映射:明细表这一轮没有可用源表,只有 order_id 一条
    verify(layerFieldMappingRepository).upsert(any(), eq("tester"));
  }

  @Test
  void deriveRejectsUnknownLayer() {
    when(layerConfigApi.resolveByCode("NOPE")).thenReturn(null);
    ModelDeriveService.DeriveRequest request =
        new ModelDeriveService.DeriveRequest(
            PROCESS_ID, "NOPE", "dwd_order", "订单派生", "MYSQL", null, null, null, null,
            null, null, null, null, null);
    ModelingException exception =
        assertThrows(ModelingException.class, () -> service.derive(request, "tester"));
    assertEquals(ModelingErrorCode.INVALID_COLUMN, exception.getErrorCode());
  }

  @Test
  void deriveRejectsFieldOutsideInheritance() {
    stubSources();
    ModelDeriveService.DeriveRequest request =
        new ModelDeriveService.DeriveRequest(
            PROCESS_ID, "DWD", "dwd_trade_order", "订单派生", "MYSQL", null, null, null,
            null, null, null, null, null,
            List.of(new ModelDeriveService.DerivedField(
                "trade_order", "not_a_column", "not_a_column", null, null, true, false, null, null)));
    ModelingException exception =
        assertThrows(ModelingException.class, () -> service.derive(request, "tester"));
    assertEquals(ModelingErrorCode.INVALID_COLUMN, exception.getErrorCode());
  }

  // ------------------------------------------------------------------ fixtures

  /** 主表 trade_order + 明细表 trade_order_detail(同名 order_id 冲突)+ 维表 crm_customer。 */
  private void stubSources() {
    when(processApi.listProcessSources(PROCESS_ID))
        .thenReturn(
            List.of(
                new ProcessApi.ProcessSourceView(1L, 3L, "trade_order", "MAIN", null),
                new ProcessApi.ProcessSourceView(2L, 3L, "trade_order_detail", "DETAIL", "a.order_id=b.order_id"),
                new ProcessApi.ProcessSourceView(3L, 3L, "crm_customer", "DIM", null)));
    when(modelRepository.listBySource(3L, "trade_order")).thenReturn(List.of(odsModel(101L)));
    when(modelRepository.listBySource(3L, "trade_order_detail")).thenReturn(List.of(odsModel(103L)));
    when(modelRepository.listBySource(3L, "crm_customer")).thenReturn(List.of(odsModel(102L)));
    when(structureService.get(101L)).thenReturn(odsStructure(101L, "trade_order", true, false));
    when(structureService.get(103L))
        .thenReturn(
            odsStructureWith(
                103L,
                "trade_order_detail",
                List.of(
                    column("order_id", "VARCHAR", 32, null, "订单ID", 9L),
                    column("item_id", "VARCHAR", 32, null, "商品ID", null),
                    column("item_amount", "DECIMAL", 18, 2, "明细金额", null))));
    when(structureService.get(102L))
        .thenReturn(
            odsStructureWith(
                102L,
                "crm_customer",
                List.of(column("cust_id", "VARCHAR", 32, null, "客户ID", null))));
    when(modelRepository.findById(101L)).thenReturn(Optional.of(odsModel(101L)));
    when(catalogReader.listColumns(eq(3L), any(), any(), eq("trade_order")))
        .thenReturn(List.of(new CatalogColumn("order_id", "VARCHAR", 12, 32, 0, false, 1, true,
            "订单ID")));
  }

  /** ODS 模型 101(trade_order):order_id 已关联 9、pay_amount 未关联、event_time 技术列。 */
  private StructureView odsStructure(
      Long modelId, String tableName, boolean linkedOrderId, boolean ignoredDetail) {
    return odsStructureWith(
        modelId,
        tableName,
        List.of(
            column("order_id", "VARCHAR", 32, null, "订单ID", linkedOrderId ? 9L : null),
            column("pay_amount", "DECIMAL", 18, 2, "支付金额", null),
            column("event_time", "DATETIME", 19, null, "业务事件时间", null)));
  }

  private StructureView odsStructureWith(
      Long modelId, String tableName, List<StructureView.ColumnView> columns) {
    return StructureView.of(
        odsModel(modelId), tableName, null,
        columns.stream()
            .map(
                column ->
                    new ColumnDefinition(
                        null, column.columnName(), column.dataType(), column.length(),
                        column.scale(), column.nullable(), null, column.comment(), null, 0,
                        column.stdTypeId(), column.stdNamingId(), column.stdCodeSetCode(),
                        column.stdUnitId(), column.stdCaliberId(), column.stdSecurityId(),
                        column.stdFieldId(), null, null, null))
            .toList(),
        List.of(), List.of(), new StructureView.PartitionView(null, List.of(), null), java.util.Map.of());
  }

  private static StructureView.ColumnView column(
      String name, String dataType, Integer length, Integer scale, String comment, Long stdFieldId) {
    return new StructureView.ColumnView(
        null, name, dataType, length, scale, Boolean.TRUE, null, comment, null, 0, null, null,
        null, null, null, null, stdFieldId, null, null, null);
  }

  private static Model odsModel(Long id) {
    return new Model(
        id, "trade_order", "订单主表", ModelDialect.MYSQL, "订单主表", ModelStatus.DRAFT, "tester",
        null, null, "ODS", null, null, null, null, null, 3L, "crm_db", "trade_order");
  }

  private static Model createdModel() {
    return new Model(
        300L, "dwd_trade_order", "订单派生", ModelDialect.MYSQL, "订单派生", ModelStatus.DRAFT,
        "tester", null, null, "DWD", PROCESS_ID, null, null, null, null);
  }


  // ------------------------------------------------- 51 DWS 聚合 / 52 ADS 应用

  /** 一个 DWD 上游模型(order_id 维度已治理、pay_amount 未治理)。 */
  private void stubDwdUpstream() {
    Model dwd = new Model(
        300L, "dwd_order_create", "下单明细", ModelDialect.MYSQL, null, ModelStatus.DRAFT,
        "tester", null, null, "DWD", PROCESS_ID, null, null, null, null);
    when(modelRepository.listByProcessLayer(PROCESS_ID, "DWD")).thenReturn(List.of(dwd));
    when(structureService.get(300L))
        .thenReturn(
            odsStructureWith(
                300L,
                "dwd_order_create",
                List.of(
                    column("order_id", "VARCHAR", 32, null, "订单ID", 9L),
                    column("pay_amount", "DECIMAL", 18, 2, "支付金额", 6L),
                    column("order_city", "VARCHAR", 64, null, "下单城市", null),
                    column("event_time", "DATETIME", 19, null, "业务事件时间", null))));
  }

  @Test
  void metricDraftResolvesMeasuresDimensionsAndUpstream() {
    when(metricQueryApi.listEnabledByIds(List.of(1L, 2L)))
        .thenReturn(
            List.of(
                new MetricQueryView(1L, "gmv", "GMV", "ATOMIC", PROCESS_ID, null,
                    "SUM(order_amount)", null, null, null, 300L, "[\"order_date\",\"order_city\"]",
                    "DAY"),
                new MetricQueryView(2L, "order_cnt", "订单量", "ATOMIC", PROCESS_ID, null,
                    "COUNT(DISTINCT order_id)", null, null, null, 300L, null, "DAY")));

    ModelDeriveService.MetricDraftView draft =
        service.metricDraft(List.of(1L, 2L), "MYSQL", DeriveLayerPolicy.DWD);

    assertEquals(List.of(PROCESS_ID), draft.processIds());
    assertEquals(List.of(300L), draft.upstreamModelIds());
    // 指标周期 DAY → 建模周期约定 1d
    assertEquals("1d", draft.statPeriod());
    // 度量:GMV=SUM(order_amount)→sum_order_amount;订单量=COUNT DISTINCT→count_distinct_order_id
    assertEquals(2, draft.measures().size());
    assertEquals("order_amount", draft.measures().get(0).sourceColumn());
    assertEquals("SUM", draft.measures().get(0).aggregateFunc());
    assertEquals("sum_order_amount", draft.measures().get(0).landingName());
    assertEquals("COUNT_DISTINCT", draft.measures().get(1).aggregateFunc());
    assertEquals("count_distinct_order_id", draft.measures().get(1).landingName());
    // 维度建议来自指标 statDimensions
    assertEquals(List.of("order_date", "order_city"), draft.dimensions());
    assertTrue(draft.warnings().isEmpty());
  }

  /** 61:数据来源选 DWS 时,上游取该过程的 DWS 模型而不是指标绑定的明细模型。 */
  @Test
  void metricDraftFromDwsResolvesProcessDwsModels() {
    when(metricQueryApi.listEnabledByIds(List.of(1L)))
        .thenReturn(
            List.of(
                new MetricQueryView(1L, "gmv", "GMV", "ATOMIC", PROCESS_ID, null,
                    "SUM(order_amount)", null, null, null, 300L, "[\"order_date\"]", "MONTH")));
    when(modelRepository.listByProcessLayer(PROCESS_ID, "DWS"))
        .thenReturn(
            List.of(
                new Model(
                    400L, "dws_order_create_1d", "下单汇总", ModelDialect.MYSQL, null,
                    ModelStatus.DRAFT, "tester", null, null, "DWS", PROCESS_ID, null, null, null,
                    null)));

    ModelDeriveService.MetricDraftView draft =
        service.metricDraft(List.of(1L), "MYSQL", DeriveLayerPolicy.DWS);

    assertEquals(List.of(400L), draft.upstreamModelIds());
    assertEquals("1m", draft.statPeriod());
  }

  /** 61:数据来源选 DWS 但该过程还没有 DWS 模型时给出可操作提示(不静默返回空清单)。 */
  @Test
  void metricDraftFromDwsWarnsWhenNoDwsModelExists() {
    when(metricQueryApi.listEnabledByIds(List.of(1L)))
        .thenReturn(
            List.of(
                new MetricQueryView(1L, "gmv", "GMV", "ATOMIC", PROCESS_ID, null,
                    "SUM(order_amount)", null, null, null, 300L, null, "DAY")));

    ModelDeriveService.MetricDraftView draft =
        service.metricDraft(List.of(1L), "MYSQL", DeriveLayerPolicy.DWS);

    assertTrue(draft.upstreamModelIds().isEmpty());
    assertTrue(draft.warnings().stream().anyMatch(warning -> warning.contains("还没有 DWS 模型")));
  }

  @Test
  void metricDraftDegradesWhenMetricModuleAbsent() {
    when(metricQueryProvider.getIfAvailable()).thenReturn(null);
    ModelDeriveService.MetricDraftView draft =
        service.metricDraft(List.of(1L), "MYSQL", DeriveLayerPolicy.DWD);
    assertTrue(draft.upstreamModelIds().isEmpty());
    assertTrue(draft.measures().isEmpty());
    assertTrue(draft.dimensions().isEmpty());
  }

  /** 61:ADS 可从 DWD 直接取数(明细实时报表),预览按覆盖后的上游解析。 */
  @Test
  void adsPreviewAcceptsDwdAsDataSourceOverride() {
    stubSources();
    Model dwd = new Model(
        300L, "dwd_order_create", "下单明细", ModelDialect.MYSQL, null, ModelStatus.DRAFT,
        "tester", null, null, "DWD", PROCESS_ID, null, null, null, null);
    when(modelRepository.listByProcessLayer(PROCESS_ID, "DWD")).thenReturn(List.of(dwd));
    when(structureService.get(300L))
        .thenReturn(
            odsStructureWith(
                300L, "dwd_order_create",
                List.of(column("order_id", "VARCHAR", 32, null, "订单ID", 9L))));

    ModelDeriveService.PreviewView view =
        service.preview(PROCESS_ID, "ADS", "MYSQL", null, null, null, "report_order", "订单报表",
            "DWD");

    assertEquals("DWD", view.upstreamLayer());
    assertEquals("APPLICATION", view.mode());
    assertEquals(1, view.upstreamModels().size());
  }

  /** 61:上游覆盖只对 ADS 生效——DWS 的上游固定是 DWD,传错参数按非法请求阻断。 */
  @Test
  void derivedLayersRejectDataSourceOverride() {
    stubSources();
    ModelingException exception =
        assertThrows(
            ModelingException.class,
            () ->
                service.preview(PROCESS_ID, "DWS", "MYSQL", null, null, null, null, null, "ODS"));
    assertEquals(ModelingErrorCode.INVALID_SEARCH, exception.getErrorCode());
    assertTrue(exception.getUserMessage().contains("仅 ADS"));
  }

  @Test
  void dwsPreviewDefaultsRolesFromStandardFieldRoles() {
    stubDwdUpstream();
    when(processApi.getField(9L)).thenReturn(field9());
    when(processApi.getField(6L)).thenReturn(metricField());
    ModelDeriveService.PreviewView view =
        service.preview(PROCESS_ID, "DWS", "MYSQL", null, null, "1d", null, null, null);

    assertTrue(view.supported());
    assertEquals("DWD", view.upstreamLayer());
    assertEquals("AGGREGATE", view.mode());
    assertEquals(1, view.upstreamModels().size());
    assertTrue(view.upstreamModels().get(0).selected());
    // METRIC 标准字段 → 度量(SUM);其余 → 维度
    Map<String, String> roles = new LinkedHashMap<>();
    view.fields().stream()
        .filter(field -> field.fieldRole() != null)
        .forEach(field -> roles.put(field.landingField(), field.fieldRole() + "/" + field.aggregateFunc()));
    assertEquals("DIMENSION/null", roles.get("order_id"));
    assertEquals("MEASURE/SUM", roles.get("pay_amount"));
    assertEquals("DIMENSION/null", roles.get("order_city"));
    assertEquals("dws_order_create_1d", view.suggestedCode());
  }

  @Test
  void dwsDeriveWritesAggregateDefinitionAndDimensionPrimaryKey() {
    stubDwdUpstream();
    when(processApi.getField(9L)).thenReturn(field9());
    when(processApi.getField(6L)).thenReturn(metricField());
    when(modelRepository.assignProcess(any(), eq(PROCESS_ID), eq("DWS"), any())).thenReturn(true);
    ModelDeriveService.PreviewView pv =
        service.preview(PROCESS_ID, "DWS", "MYSQL", null, null, "1d", null, null, null);
    List<ModelDeriveService.DerivedField> chosen =
        pv.fields().stream()
            .filter(ModelDeriveService.FieldView::include)
            .map(
                field ->
                    new ModelDeriveService.DerivedField(
                        field.sourceTable(), field.sourceColumn(), field.landingField(),
                        field.stdFieldId(), null, true, field.technical(), field.fieldRole(),
                        field.aggregateFunc()))
            .toList();
    ModelDeriveService.DeriveRequest request =
        new ModelDeriveService.DeriveRequest(
            PROCESS_ID, "DWS", "dws_order_create_1d", "下单汇总", "MYSQL", null, null, null, null,
            "1d", null, null, null, chosen);
    when(catalogService.create(any(), eq("dws_order_create_1d"), any(), any(), eq("tester"), any(), any(), any()))
        .thenReturn(
            new Model(
                400L, "dws_order_create_1d", "下单汇总", ModelDialect.MYSQL, null,
                ModelStatus.DRAFT, "tester", null, null, "DWS", PROCESS_ID, null, null, null,
                null));

    ModelDeriveService.DeriveView view = service.derive(request, "tester");

    ArgumentCaptor<ModelingStructureApi.SaveStructureRequest> saved =
        ArgumentCaptor.forClass(ModelingStructureApi.SaveStructureRequest.class);
    verify(structureService).save(eq(400L), saved.capture(), eq("tester"));
    // 主键 = 全部维度字段(order_id、order_city)
    assertEquals(List.of("order_id", "order_city"), saved.getValue().primaryKey());
    // 43 落角色 + 聚合函数;pay_amount 是度量 SUM
    ArgumentCaptor<io.yak.ops.common.bean.po.modeling.ModelingLayerFieldMappingPO> mapping =
        ArgumentCaptor.forClass(io.yak.ops.common.bean.po.modeling.ModelingLayerFieldMappingPO.class);
    // 聚合层:全部业务字段都落 43(order_id/pay_amount/order_city),未治理字段 process_field_id 为空
    verify(layerFieldMappingRepository, org.mockito.Mockito.times(3))
        .upsert(mapping.capture(), eq("tester"));
    Map<String, io.yak.ops.common.bean.po.modeling.ModelingLayerFieldMappingPO> byName =
        new LinkedHashMap<>();
    mapping.getAllValues().forEach(row -> byName.put(row.getLayerFieldName(), row));
    // 度量:pay_amount 落 MEASURE + SUM,来源 = <上游模型编码>.<列>
    assertEquals("MEASURE", byName.get("pay_amount").getFieldRole());
    assertEquals("SUM", byName.get("pay_amount").getAggregateFunc());
    assertEquals("dwd_order_create.pay_amount", byName.get("pay_amount").getSourceField());
    // 维度:order_id 落 DIMENSION 且不带聚合函数
    assertEquals("DIMENSION", byName.get("order_id").getFieldRole());
    assertEquals(null, byName.get("order_id").getAggregateFunc());
    // 未治理字段(order_city)也落 43(DWS 粒度必需),process_field_id 为空
    assertEquals("DIMENSION", byName.get("order_city").getFieldRole());
    assertEquals(null, byName.get("order_city").getProcessFieldId());
    // 聚合层不写 19 来源映射,跳过条数回报
    verify(mappingService, never()).setMapping(any(), any(), any(), any(), any(), any(), any(), any(), any());
    assertEquals(3, view.sourceMappingSkipped());
    // 统计周期与应用绑定落模型行
    verify(modelRepository).assignAggregateMeta(400L, "1d", null, null, "tester");
  }

  @Test
  void dwsDeriveRejectsMeasureWithoutAggregateFunc() {
    stubDwdUpstream();
    when(processApi.getField(9L)).thenReturn(field9());
    when(processApi.getField(6L)).thenReturn(metricField());
    ModelDeriveService.DeriveRequest request =
        new ModelDeriveService.DeriveRequest(
            PROCESS_ID, "DWS", "dws_order_create_1d", "下单汇总", "MYSQL", null, null, null, null,
            "1d", null, null, null,
            List.of(
                new ModelDeriveService.DerivedField(
                    "dwd_order_create", "pay_amount", "pay_amount", 6L, null, true, false,
                    "MEASURE", null)));
    ModelingException exception =
        assertThrows(ModelingException.class, () -> service.derive(request, "tester"));
    assertEquals(ModelingErrorCode.INVALID_COLUMN, exception.getErrorCode());
    assertTrue(exception.getUserMessage().contains("必须指定聚合函数"));
  }

  @Test
  void dwsDeriveRejectsUnknownAggregateFunc() {
    stubDwdUpstream();
    when(processApi.getField(9L)).thenReturn(field9());
    when(processApi.getField(6L)).thenReturn(metricField());
    ModelDeriveService.DeriveRequest request =
        new ModelDeriveService.DeriveRequest(
            PROCESS_ID, "DWS", "dws_order_create_1d", "下单汇总", "MYSQL", null, null, null, null,
            "1d", null, null, null,
            List.of(
                new ModelDeriveService.DerivedField(
                    "dwd_order_create", "pay_amount", "pay_amount", 6L, null, true, false,
                    "MEASURE", "MEDIAN")));
    ModelingException exception =
        assertThrows(ModelingException.class, () -> service.derive(request, "tester"));
    assertEquals(ModelingErrorCode.INVALID_COLUMN, exception.getErrorCode());
    assertTrue(exception.getUserMessage().contains("聚合函数不合法"));
  }

  @Test
  void adsDeriveBindsApplicationAndLeavesNoPrimaryKey() {
    Model dws = new Model(
        400L, "dws_order_create_1d", "下单汇总", ModelDialect.MYSQL, null, ModelStatus.DRAFT,
        "tester", null, null, "DWS", PROCESS_ID, null, null, null, null);
    when(modelRepository.listByProcessLayer(PROCESS_ID, "DWS")).thenReturn(List.of(dws));
    when(structureService.get(400L))
        .thenReturn(
            odsStructureWith(
                400L,
                "dws_order_create_1d",
                List.of(
                    column("order_city", "VARCHAR", 64, null, "下单城市", null),
                    column("pay_amount", "DECIMAL", 18, 2, "支付金额", 6L))));
    when(processApi.getField(6L)).thenReturn(metricField());
    when(modelRepository.assignProcess(any(), eq(PROCESS_ID), eq("ADS"), any())).thenReturn(true);
    when(catalogService.create(any(), eq("ads_report_order"), any(), any(), eq("tester"), any(), any(), any()))
        .thenReturn(
            new Model(
                500L, "ads_report_order", "订单报表", ModelDialect.MYSQL, null, ModelStatus.DRAFT,
                "tester", null, null, "ADS", PROCESS_ID, null, null, null, null));
    ModelDeriveService.PreviewView pv =
        service.preview(PROCESS_ID, "ADS", "MYSQL", null, null, null, "report_order", "订单报表", null);
    assertEquals("APPLICATION", pv.mode());
    assertEquals("ads_report_order_order_create", pv.suggestedCode());
    List<ModelDeriveService.DerivedField> chosen =
        pv.fields().stream()
            .filter(ModelDeriveService.FieldView::include)
            .map(
                field ->
                    new ModelDeriveService.DerivedField(
                        field.sourceTable(), field.sourceColumn(), field.landingField(),
                        field.stdFieldId(), null, true, field.technical(), field.fieldRole(),
                        field.aggregateFunc()))
            .toList();
    ModelDeriveService.DeriveRequest request =
        new ModelDeriveService.DeriveRequest(
            PROCESS_ID, "ADS", "ads_report_order", "订单报表", "MYSQL", null, null, null, null,
            null, "report_order", "订单报表", null, chosen);

    ModelDeriveService.DeriveView view = service.derive(request, "tester");

    ArgumentCaptor<ModelingStructureApi.SaveStructureRequest> saved =
        ArgumentCaptor.forClass(ModelingStructureApi.SaveStructureRequest.class);
    verify(structureService).save(eq(500L), saved.capture(), eq("tester"));
    // ADS 默认不设主键
    assertTrue(saved.getValue().primaryKey().isEmpty());
    // 应用绑定落模型行
    verify(modelRepository).assignAggregateMeta(500L, null, "report_order", "订单报表", "tester");
    assertEquals(2, view.sourceMappingSkipped());
  }

  private static StandardField metricField() {
    return new StandardField(6L, "pay_amount", "支付金额", "METRIC", StandardField.STATUS_ENABLED,
        "DECIMAL", 191L, null, null, null, null, "实付金额", StandardField.SOURCE_MANUAL, 1, false,
        null, null, null);
  }

  private static StandardField field9() {
    return new StandardField(9L, "order_id", "订单ID", "PROCESS", StandardField.STATUS_ENABLED,
        "VARCHAR", 190L, null, null, null, null, "订单主键", StandardField.SOURCE_MANUAL, 1, true,
        null, null, null);
  }

  private static StandardField field10() {
    return new StandardField(10L, "settle_channel", "结算渠道", "DIMENSION",
        StandardField.STATUS_ENABLED, "VARCHAR", 191L, null, null, null, null, null,
        StandardField.SOURCE_MANUAL, 1, false, null, null, null);
  }

  private ModelDeriveService.DeriveRequest deriveRequest() {
    return new ModelDeriveService.DeriveRequest(
        PROCESS_ID,
        "DWD",
        "dwd_trade_order",
        "订单派生",
        "MYSQL",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(
            new ModelDeriveService.DerivedField(
                "trade_order", "order_id", "order_id", null, null, true, false, null, null),
            new ModelDeriveService.DerivedField(
                "trade_order", "pay_amount", "pay_amount", null, null, true, false, null, null)));
  }
}
