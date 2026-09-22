package io.yak.ops.business.modeling.derive;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.api.ModelingStructureApi;
import io.yak.ops.business.modeling.catalog.ModelCatalogService;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.governance.StandardFieldMatcher;
import io.yak.ops.business.modeling.lineage.ModelingLineageRegistrationService;
import io.yak.ops.business.modeling.mapping.MappingService;
import io.yak.ops.business.modeling.repository.LayerFieldMappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureDialectCatalog;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.semantic.api.LayerConfigApi;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.StandardRecommendApi;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.common.bean.po.modeling.ModelingLayerFieldMappingPO;
import io.yak.ops.common.api.metric.MetricQueryApi;
import io.yak.ops.common.api.metric.MetricQueryView;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 按业务过程派生建模(ticket 44):字段来自该源表对应的 **ODS 模型**(继承),标准字段关联
 * (std_field_id)随字段继承;匹配规则由 {@link StandardFieldMatcher} 单一实现(38/44 共用)。
 *
 * <p>选表按 36 的 {@code tableRole}:MAIN 唯一且为粒度驱动表,DETAIL 按绑定顺序追加,
 * DIM 维表字段**默认不纳入**(退化维度可显式勾选),但 **DWD 目标默认宽表(D3/v2.0 59)**:DIM
 * 维表字段默认纳入做维度退化,前端可一键排除成纯事实表。ODS 与目标层技术列不参与继承
 * (目标层技术列按该层规则补),同名字段先到先得(MAIN 优先),类型按目标方言校验
 * (不支持时经类型标准映射,仍不支持则阻断)。
 *
 * <p>派生上游按 {@link DeriveLayerPolicy} 的能力矩阵解析;唯一可覆盖的是 ADS 的数据来源(61):
 * 默认从 DWS 反推,明细/实时报表可显式改为从 DWD 取数({@code upstreamLayer=DWD}),其余分层传覆盖即阻断。
 *
 * <p>落库(同一事务):模型 + 字段 + 19 来源映射(源 = ODS 模型绑定的源表/列,可校验才写)
 * + 43 分层映射(process_field_id 由 std_field_id 派生)+ 血缘登记;并把本次确定的标准字段关联
 * **回写**到 ODS 字段列,让治理结果沉淀在 ODS 层。
 */
@Component
public class ModelDeriveService {

  private static final String ODS_LAYER_CODE = "ODS";
  private static final String ROLE_MAIN = "MAIN";
  private static final String ROLE_DETAIL = "DETAIL";
  private static final String ROLE_DIM = "DIM";
  private static final String TARGET_ROLE = "TARGET";

  /** 聚合层字段角色(51/52)。 */
  private static final String FIELD_ROLE_DIMENSION = "DIMENSION";
  private static final String FIELD_ROLE_MEASURE = "MEASURE";

  /** 允许的聚合函数白名单(结构化定义,不接受任意文本)。 */
  private static final Set<String> AGGREGATE_FUNCS =
      Set.of("SUM", "COUNT", "COUNT_DISTINCT", "MAX", "MIN", "AVG");

  /** 统计周期可选值(表级约定)。 */
  private static final Set<String> STAT_PERIODS = Set.of("1h", "1d", "1w", "1m", "ALL");

  /** 不参与继承的源列:ODS 技术列(贴源层的落地标记,不属于派生模型)。 */
  private static final Set<String> INHERITANCE_EXCLUDED =
      Set.of("process_time", "event_time");

  /** 目标层技术列(按该层规则单独补;顺序即落地顺序)。 */
  private static final Map<String, TechnicalDef> TARGET_TECHNICALS = new LinkedHashMap<>();

  static {
    TARGET_TECHNICALS.put(
        "process_time", new TechnicalDef("process_time", "DATETIME", null, "数据处理时间"));
    TARGET_TECHNICALS.put(
        "event_time", new TechnicalDef("event_time", "DATETIME", null, "业务事件时间"));
  }

  private final ProcessApi processApi;
  private final LayerConfigApi layerConfigApi;
  private final StandardQueryApi standardQueryApi;
  private final StandardRecommendApi recommendApi;
  private final DataSourceCatalogReader catalogReader;
  private final ModelCatalogService catalogService;
  private final ModelStructureService structureService;
  private final io.yak.ops.business.modeling.version.ModelPublishedStructureReader structureReader;
  private final ModelRepository modelRepository;
  private final LayerFieldMappingRepository layerFieldMappingRepository;
  private final MappingService mappingService;
  private final StandardFieldMatcher fieldMatcher;
  private final DeriveLayerPolicy layerPolicy;
  private final DimConventions dimConventions;
  private final ModelingLineageRegistrationService lineageRegistrationService;
  /** 60:指标只读 SPI(common 定义,metric 模块实现;ObjectProvider 优雅降级=无指标模块时按空处理)。 */
  private final ObjectProvider<MetricQueryApi> metricQueryProvider;

  public ModelDeriveService(
      ProcessApi processApi,
      LayerConfigApi layerConfigApi,
      StandardQueryApi standardQueryApi,
      StandardRecommendApi recommendApi,
      DataSourceCatalogReader catalogReader,
      ModelCatalogService catalogService,
      ModelStructureService structureService,
      io.yak.ops.business.modeling.version.ModelPublishedStructureReader structureReader,
      ModelRepository modelRepository,
      LayerFieldMappingRepository layerFieldMappingRepository,
      MappingService mappingService,
      StandardFieldMatcher fieldMatcher,
      DeriveLayerPolicy layerPolicy,
      DimConventions dimConventions,
      ModelingLineageRegistrationService lineageRegistrationService,
      ObjectProvider<MetricQueryApi> metricQueryProvider) {
    this.processApi = processApi;
    this.layerConfigApi = layerConfigApi;
    this.standardQueryApi = standardQueryApi;
    this.recommendApi = recommendApi;
    this.catalogReader = catalogReader;
    this.catalogService = catalogService;
    this.structureService = structureService;
    this.structureReader = structureReader;
    this.modelRepository = modelRepository;
    this.layerFieldMappingRepository = layerFieldMappingRepository;
    this.mappingService = mappingService;
    this.fieldMatcher = fieldMatcher;
    this.layerPolicy = layerPolicy;
    this.dimConventions = dimConventions;
    this.lineageRegistrationService = lineageRegistrationService;
    this.metricQueryProvider = metricQueryProvider;
  }

  /** 派生请求:fields 为用户确认后的字段清单(sourceTable + sourceColumn 定位继承来源)。 */
  public record DeriveRequest(
      Long processId,
      String layerCode,
      String code,
      String name,
      String dialect,
      String description,
      Long directoryId,
      /** 维表 SCD 类型(50):SCD1 默认/SCD2;仅 DIM 目标生效。 */
      String scdType,
      /** 51/52:参与的上游模型 ID(缺省=该过程该上游层的全部模型)。 */
      List<Long> upstreamModelIds,
      /** 51:统计周期(1h/1d/1w/1m/ALL;仅聚合层生效)。 */
      String statPeriod,
      /** 52:应用/报表绑定(ADS)。 */
      String appCode,
      String appName,
      /** 61:ADS 数据来源覆盖(DWS 默认/DWD 明细实时报表;仅 APPLICATION 模式生效)。 */
      String upstreamLayer,
      List<DerivedField> fields) {}

  /** 一条待落地字段:落地名可改、标准字段可关联/沉淀、转换表达式可填、是否纳入。 */
  public record DerivedField(
      String sourceTable,
      String sourceColumn,
      String landingField,
      Long stdFieldId,
      String transformExpr,
      boolean include,
      boolean technical,
      /** 聚合层字段角色(51/52):DIMENSION 分组键 / MEASURE 度量;非聚合层忽略。 */
      String fieldRole,
      /** 聚合函数(MEASURE 必填,白名单内)。 */
      String aggregateFunc) {}

  /** 派生结果:计数 + 治理率 + 回填/映射统计 + 血缘资产。 */
  public record DeriveView(
      Long modelId,
      String code,
      String name,
      int columnCount,
      int governedCount,
      int unmatchedCount,
      int governanceRate,
      int backfilledCount,
      int sourceMappingSkipped,
      Long tableAssetId) {}

  /** 预览:按源表分组与逐字段治理清单;只读,不写库。 */
  public record PreviewView(
      Long processId,
      String layerCode,
      /** 该目标分层的上游分层(49);不支持的分层也为空。 */
      String upstreamLayer,
      /** 该目标分层当前是否支持派生。 */
      boolean supported,
      /** 不支持时的原因(说明上游是什么、缺什么能力、指向哪张票)。 */
      String unsupportedReason,
      List<SourceView> sources,
      List<FieldView> fields,
      int totalFields,
      int matchedFields,
      int unmatchedFields,
      int conflictFields,
      int technicalFields,
      /** 维表约定列数(代理键/SCD,50)。 */
      int conventionFields,
      int governanceRate,
      ExistingModel existingModel,
      List<String> warnings,
      /** 派生模式(49):INHERIT/AGGREGATE/APPLICATION。 */
      String mode,
      /** 聚合/应用层的上游模型候选(51/52)。 */
      List<UpstreamModelView> upstreamModels,
      /** 命名建议(51/52:含周期或应用编码;INHERIT:层_过程)。 */
      String suggestedCode,
      String suggestedName) {}

  /** 一个源表的继承情况:角色、对应 ODS 模型、是否就绪。 */
  public record SourceView(
      Long bindingId,
      Long datasourceId,
      String sourceTable,
      String tableRole,
      String joinCondition,
      Long odsModelId,
      String odsModelCode,
      String odsTableName,
      boolean ready,
      int fieldCount,
      String note) {}

  /**
   * 逐字段治理明细:命中标准字段与匹配方式、落地名、冲突合并、技术列标记。
   * 名称相似只给建议(suggested*)不自动关联,由用户确认后随派生落库。
   */
  public record FieldView(
      String sourceTable,
      String tableRole,
      Long odsModelId,
      String sourceColumn,
      String dataType,
      Integer length,
      Integer scale,
      Boolean nullable,
      String comment,
      Long stdFieldId,
      String stdFieldCode,
      String stdFieldName,
      String matchedBy,
      String landingField,
      boolean include,
      boolean conflicting,
      String conflictWith,
      boolean technical,
      /** 维表约定列(代理键/SCD 生效起止,50):结构性字段,不计入治理率。 */
      boolean convention,
      /** 聚合层字段角色(51/52):DIMENSION/MEASURE;NULL=非聚合层。 */
      String fieldRole,
      String aggregateFunc,
      Long suggestedStdFieldId,
      String suggestedStdFieldName) {}

  /** 同业务过程同分层已有模型(防重提示)。 */
  public record ExistingModel(Long modelId, String code, String name) {}

  /** 聚合层字段定义(51/52):DIMENSION 分组键 / MEASURE 度量 + 聚合函数。 */
  public record AggregateSpec(String fieldRole, String aggregateFunc) {}

  /** 聚合/应用层的上游模型候选(51/52;上游是模型而不是数据源表)。 */
  public record UpstreamModelView(
      Long modelId, String code, String name, String layerCode, boolean selected, int fieldCount) {}

  /**
   * 内部:一条解析后的字段。
   * {@code odsColumn} 为空表示"仅目标层技术列"(没有继承来源,类型取 {@code technicalDef});
   * 标准字段关联优先继承 ODS 列,缺失时按过程字段集匹配。
   */
  private record ResolvedField(
      String sourceTable,
      String tableRole,
      Long odsModelId,
      String odsTableName,
      StructureView.ColumnView odsColumn,
      String overrideDataType,
      Long stdFieldId,
      StandardField stdField,
      String matchedBy,
      String landingField,
      String transformExpr,
      boolean include,
      TechnicalDef technicalDef,
      String conflictWith,
      StandardFieldMatcher.Match suggestion,
      DimConventions.ConventionField convention,
      AggregateSpec aggregate) {

    String columnName() {
      if (technicalDef != null) {
        return technicalDef.name();
      }
      return odsColumn != null ? odsColumn.columnName() : landingField;
    }

    String dataType() {
      if (overrideDataType != null) {
        return overrideDataType;
      }
      return odsColumn != null ? odsColumn.dataType() : technicalDef.dataType();
    }

    Integer length() {
      if (odsColumn != null) {
        return odsColumn.length();
      }
      return technicalDef != null ? technicalDef.length() : convention.length();
    }

    Integer scale() {
      return odsColumn == null ? null : odsColumn.scale();
    }

    Boolean nullable() {
      return odsColumn == null ? Boolean.TRUE : odsColumn.nullable();
    }

    String comment() {
      if (odsColumn != null) {
        return odsColumn.comment();
      }
      return technicalDef != null ? technicalDef.note() : convention.note();
    }

    boolean technical() {
      return technicalDef != null;
    }

    boolean isConvention() {
      return convention != null;
    }

    boolean isMeasure() {
      return aggregate != null && FIELD_ROLE_MEASURE.equals(aggregate.fieldRole());
    }

    /** 业务字段:既非管道技术列,也非维表约定列(治理率只统计业务字段)。 */
    boolean business() {
      return technicalDef == null && convention == null;
    }

    /** 同名列冲突标注:记录该字段在哪些表里重复出现。 */
    ResolvedField withConflict(String duplicateSourceTable) {
      return new ResolvedField(
          sourceTable, tableRole, odsModelId, odsTableName, odsColumn, overrideDataType,
          stdFieldId, stdField, matchedBy, landingField, transformExpr, include, technicalDef,
          duplicateSourceTable, suggestion, convention, aggregate);
    }
  }

  /** 目标层技术列定义(名称/类型/长度/注释)。 */
  private record TechnicalDef(String name, String dataType, Integer length, String note) {}

  // ------------------------------------------------------------------ preview

  /** 只读预览:源表就绪情况 + 字段继承清单 + 治理率 + 防重提示;dialect/scdType 决定约定列与类型。 */
  public PreviewView preview(
      Long processId,
      String layerCode,
      String dialect,
      String scdType,
      List<Long> upstreamModelIds,
      String statPeriod,
      String appCode,
      String appName,
      String upstreamLayerOverride) {
    if (processId == null) {
      throw new ModelingException(ModelingErrorCode.INVALID_SEARCH, "必须选择业务过程");
    }
    WarehouseLayer layer = requireLayer(layerCode);
    ModelDialect targetDialect =
        ModelDialect.fromStored(dialect == null ? "MYSQL" : dialect).orElse(ModelDialect.MYSQL);
    DeriveLayerPolicy.LayerSupport support = layerPolicy.resolve(layer);
    if (!support.supported()) {
      // 不支持的分层:只回报上游与原因,不解析继承(解析会给出误导性的"可继承字段"清单)。
      return new PreviewView(
          processId, layer.code(), support.upstreamLayer(), false, support.reason(),
          List.of(), List.of(), 0, 0, 0, 0, 0, 0, 0, null,
          List.of(support.reason()), support.mode().name(), List.of(), null, null);
    }
    String upstream = effectiveUpstreamLayer(support, upstreamLayerOverride);
    String processCode = resolveProcessCode(processId);
    DeriveLayerPolicy.DeriveMode mode = support.mode();
    List<Model> upstreamCandidates =
        DeriveLayerPolicy.isAggregateMode(mode)
            ? modelRepository.listByProcessLayer(processId, upstream)
            : List.of();
    if (DeriveLayerPolicy.isAggregateMode(mode) && upstreamCandidates.isEmpty()) {
      // 上游是模型而不是数据源表:该过程还没有上层模型时无法派生(比"字段清单为空"更明确)
      String blockedReason =
          "该业务过程还没有 " + upstream + " 模型(上游缺失),请先派生 "
              + upstream;
      return new PreviewView(
          processId, layer.code(), upstream, false, blockedReason, List.of(),
          List.of(), 0, 0, 0, 0, 0, 0, 0, null, List.of(blockedReason), mode.name(), List.of(),
          null, null);
    }
    Resolution resolution =
        resolve(
            processId, layer, processCode, targetDialect, scdType, mode, upstream,
            upstreamModelIds);
    List<ResolvedField> included = resolution.fields().stream().filter(ResolvedField::include).toList();
    // 治理率只统计业务字段(排除管道技术列与维表约定列)
    List<ResolvedField> business =
        included.stream().filter(ResolvedField::business).toList();
    int matched = (int) business.stream().filter(field -> field.stdFieldId() != null).count();
    int conflicts = (int) resolution.fields().stream()
        .filter(field -> field.conflictWith() != null)
        .count();
    int technicals = (int) resolution.fields().stream().filter(ResolvedField::technical).count();
    int conventions = (int) resolution.fields().stream().filter(ResolvedField::isConvention).count();
    return new PreviewView(
        processId,
        layer.code(),
        upstream,
        true,
        null,
        resolution.sources(),
        resolution.fields().stream().map(ModelDeriveService::toFieldView).toList(),
        included.size(),
        matched,
        business.size() - matched,
        conflicts,
        technicals,
        conventions,
        rate(matched, business.size()),
        resolution.existingModel(),
        resolution.warnings(),
        mode.name(),
        upstreamCandidates.stream()
            .map(
                model ->
                    new UpstreamModelView(
                        model.id(), model.code(), model.name(), model.layerCode(),
                        upstreamModelIds == null
                            || upstreamModelIds.isEmpty()
                            || upstreamModelIds.contains(model.id()),
                        structureService.get(model.id()).columns().size()))
            .toList(),
        suggestedCode(layer, mode, processCode, statPeriod, appCode),
        suggestedName(processCode, layer, appName));
  }

  // ------------------------------------------------------------------- derive

  /** 派生:按预览确认结果建模型草稿并落 19/43 映射、治理回填、血缘登记(同一事务)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DeriveView derive(DeriveRequest request, String operator) {
    if (request.processId() == null) {
      throw new ModelingException(ModelingErrorCode.INVALID_SEARCH, "必须选择业务过程");
    }
    if (!StringUtils.hasText(request.code()) || !StringUtils.hasText(request.name())) {
      throw new ModelingException(ModelingErrorCode.INVALID_SEARCH, "模型编码与名称不能为空");
    }
    WarehouseLayer layer = requireLayer(request.layerCode());
    DeriveLayerPolicy.LayerSupport support = layerPolicy.resolve(layer);
    if (!support.supported()) {
      throw new ModelingException(
          ModelingErrorCode.LAYER_DERIVE_UNSUPPORTED,
          "目标分层 " + layer.code() + " 暂不支持派生：" + support.reason());
    }
    ModelDialect dialect =
        ModelDialect.fromStored(request.dialect())
            .orElseThrow(
                () -> new ModelingException(ModelingErrorCode.INVALID_DIALECT, request.dialect()));

    String processCode = resolveProcessCode(request.processId());
    DeriveLayerPolicy.DeriveMode mode = support.mode();
    String upstream = effectiveUpstreamLayer(support, request.upstreamLayer());
    if (mode == DeriveLayerPolicy.DeriveMode.AGGREGATE) {
      validateStatPeriod(request.statPeriod());
    }
    if (DeriveLayerPolicy.isAggregateMode(mode)
        && modelRepository
            .listByProcessLayer(request.processId(), upstream)
            .isEmpty()) {
      throw new ModelingException(
          ModelingErrorCode.LAYER_DERIVE_UNSUPPORTED,
          "目标分层 " + layer.code() + " 的上游 " + upstream
              + " 没有可用模型,请先派生上游分层");
    }
    Resolution resolution =
        resolve(
            request.processId(), layer, processCode, dialect, request.scdType(), mode,
            upstream, request.upstreamModelIds());
    if (resolution.existingModel() != null) {
      throw new ModelingException(
          ModelingErrorCode.DERIVE_CONFLICT,
          "该业务过程在 " + layer.code() + " 层已有模型：" + resolution.existingModel().code()
              + "（同一过程同一层只保留一个模型）");
    }
    List<ResolvedField> selection = mergeSelection(request, resolution, mode);
    if (selection.isEmpty()) {
      throw new ModelingException(ModelingErrorCode.INVALID_SEARCH, "派生至少需要勾选一个字段");
    }

    List<ModelingStructureApi.ColumnInput> columns = new ArrayList<>();
    List<ResolvedField> governed = new ArrayList<>();
    // 43 落库范围:INHERIT 只落已治理字段(44 规则);聚合/应用层落**全部业务字段**——
    // 维度/度量与聚合函数是建模语义(粒度与口径),未治理字段也必须落库,否则加工无法生成(51)。
    List<ResolvedField> mappingRows = new ArrayList<>();
    int businessCount = 0;
    for (ResolvedField field : selection) {
      columns.add(toColumnInput(field, dialect));
      if (!field.business()) {
        continue;
      }
      businessCount++;
      if (field.stdFieldId() != null) {
        governed.add(field);
      }
      if (DeriveLayerPolicy.isAggregateMode(mode) || field.stdFieldId() != null) {
        mappingRows.add(field);
      }
    }

    Model model =
        catalogService.create(
            request.name().trim(), request.code().trim(), dialect.name(), request.description(),
            operator, request.directoryId(), request.layerCode(), request.processId());
    // 主键规则:DIM = 代理键(50);DWS = 全部维度字段(分组键即粒度,51);ADS 不设(52)
    List<String> primaryKey =
        switch (mode) {
          case INHERIT ->
              selection.stream()
                  .filter(field -> field.convention() != null && field.convention().surrogateKey())
                  .map(ResolvedField::landingField)
                  .toList();
          case AGGREGATE ->
              selection.stream()
                  .filter(field -> field.aggregate() != null && !field.isMeasure())
                  .map(ResolvedField::landingField)
                  .toList();
          case APPLICATION -> List.<String>of();
        };
    structureService.save(
        model.id(),
        new ModelingStructureApi.SaveStructureRequest(
            request.code().trim(),
            request.description(),
            columns,
            primaryKey,
            List.of(),
            layerPartition(layer),
            null),
        operator);
    modelRepository.assignProcess(model.id(), request.processId(), layer.code(), operator);
    // 血缘追溯:派生建模的字段来源为业务过程
    modelRepository.assignImportLineage(model.id(), "BUSINESS_PROCESS", null, operator);

    // 43 分层映射:process_field_id 由继承的标准字段派生(未治理字段没有这一行)。
    for (ResolvedField field : mappingRows) {
      ModelingLayerFieldMappingPO po = new ModelingLayerFieldMappingPO();
      po.setModelId(model.id());
      po.setProcessFieldId(field.stdFieldId());
      po.setLayerId(layer.id());
      po.setLayerFieldName(field.landingField());
      po.setLayerDataType(field.dataType());
      po.setSourceField(field.odsTableName() + "." + field.columnName());
      po.setTransformExpr(field.transformExpr());
      if (field.aggregate() != null) {
        // 51/52:聚合定义结构化落库(角色 + 聚合函数)
        po.setFieldRole(field.aggregate().fieldRole());
        po.setAggregateFunc(field.aggregate().aggregateFunc());
      }
      layerFieldMappingRepository.upsert(po, operator);
    }

    // 19 来源映射:源 = ODS 模型绑定的源表/列;源列不可校验时跳过并计数(不阻断派生)。
    // 聚合/应用层的上游是**模型**而不是数据源表,19 表表达不了"来自哪个模型":来源由 43 的
    // source_field(<上游模型编码>.<列>)承载,故不写 19 并回报条数(见 51/52 契约)。
    int sourceMappingSkipped =
        DeriveLayerPolicy.isAggregateMode(mode)
            ? (int) selection.stream().filter(ResolvedField::business).count()
            : writeSourceMappings(model.id(), selection, operator);
    // 治理回填:只对 INHERIT(上游是 ODS 模型)生效;聚合层的上游 DWD 字段已在自身派生时治理。
    int backfilled =
        DeriveLayerPolicy.isAggregateMode(mode) ? 0 : backfillOdsStdField(selection, operator);
    if (DeriveLayerPolicy.isAggregateMode(mode)) {
      modelRepository.assignAggregateMeta(
          model.id(),
          request.statPeriod(),
          mode == DeriveLayerPolicy.DeriveMode.APPLICATION ? request.appCode() : null,
          mode == DeriveLayerPolicy.DeriveMode.APPLICATION ? request.appName() : null,
          operator);
    }

    ModelingLineageRegistrationService.RegisterView lineage =
        lineageRegistrationService.registerModel(model.id(), operator);

    return new DeriveView(
        model.id(),
        model.code(),
        model.name(),
        columns.size(),
        governed.size(),
        businessCount - governed.size(),
        rate(governed.size(), businessCount),
        backfilled,
        sourceMappingSkipped,
        lineage.tableAssetId());
  }

  // -------------------------------------------------------------- resolution

  /** 解析结果:源表视图 + 继承字段 + 防重模型 + 警告。 */
  private record Resolution(
      List<SourceView> sources,
      List<ResolvedField> fields,
      ExistingModel existingModel,
      List<String> warnings) {}

  /**
   * 解析继承:定位各源表对应的 ODS 模型 → 按 MAIN/DETAIL/DIM 顺序合并字段 →
   * 排除技术列、同名先到先得 → 标准字段关联(继承优先,缺失时按过程字段集匹配)。
   */
  private Resolution resolve(
      Long processId, WarehouseLayer layer, String processCode, ModelDialect dialect,
      String scdType, DeriveLayerPolicy.DeriveMode mode, String upstreamLayer,
      List<Long> upstreamModelIds) {
    if (DeriveLayerPolicy.isAggregateMode(mode)) {
      return resolveAggregate(
          processId, layer, mode, upstreamLayer, upstreamModelIds, processCode);
    }
    List<ProcessApi.ProcessSourceView> bindings = processApi.listProcessSources(processId);
    List<String> warnings = new ArrayList<>();
    if (bindings.isEmpty()) {
      warnings.add("该业务过程还没有关联源表（ticket 36），请先在业务过程工作台关联源表");
    }
    long mainCount =
        bindings.stream().filter(binding -> ROLE_MAIN.equals(binding.tableRole())).count();
    if (!bindings.isEmpty() && mainCount == 0) {
      warnings.add("关联源表缺少主表（MAIN）角色，无法确定派生粒度，请回业务过程补齐源表角色");
    }
    if (mainCount > 1) {
      warnings.add("关联源表存在多个主表（MAIN），请只保留一个主表");
    }
    List<StandardField> processFields = processApi.getFieldSets(processId);

    List<SourceView> sourceViews = new ArrayList<>();
    Map<String, ResolvedField> merged = new LinkedHashMap<>();
    for (String role : List.of(ROLE_MAIN, ROLE_DETAIL, ROLE_DIM)) {
      for (ProcessApi.ProcessSourceView binding : bindings) {
        if (!role.equals(binding.tableRole())) {
          continue;
        }
        Model odsModel = findOdsModel(binding);
        if (odsModel == null) {
          sourceViews.add(
              new SourceView(
                  binding.id(), binding.datasourceId(), binding.sourceTable(), binding.tableRole(),
                  binding.joinCondition(), null, null, null, false, 0,
                  "未找到该源表对应的 ODS 模型，请先逆向导入"));
          warnings.add("源表 " + binding.sourceTable() + " 没有对应的 ODS 模型，请先逆向导入");
          continue;
        }
        StructureView structure = structureReader.publishedStructure(odsModel.id());
        String odsTableName =
            StringUtils.hasText(structure.tableName()) ? structure.tableName() : odsModel.code();
        int fieldCount = 0;
        for (StructureView.ColumnView column : structure.columns()) {
          String key = column.columnName().toLowerCase(Locale.ROOT);
          if (INHERITANCE_EXCLUDED.contains(key)) {
            continue;
          }
          ResolvedField previous = merged.get(key);
          if (previous != null) {
            // 先到先得(MAIN 优先):后出现的同名列只标注冲突来源,不覆盖先到的字段。
            merged.put(key, previous.withConflict(binding.sourceTable()));
            continue;
          }
          StandardField inherited = safeGetField(column.stdFieldId());
          StandardFieldMatcher.Match suggestion =
              inherited != null ? null : fieldMatcher.match(
                  column.columnName(), column.dataType(), column.comment(), processFields);
          boolean authoritative =
              suggestion != null && StandardFieldMatcher.isAuthoritative(suggestion.matchedBy());
          StandardField matched =
              inherited != null
                  ? inherited
                  : authoritative ? safeGetField(suggestion.stdFieldId()) : null;
          merged.put(
              key,
              new ResolvedField(
                  binding.sourceTable(),
                  binding.tableRole(),
                  odsModel.id(),
                  odsTableName,
                  column,
                  null,
                  matched != null ? matched.id() : null,
                  matched,
                  matched == null
                      ? null
                      : (inherited != null ? "inherited" : suggestion.matchedBy()),
                  matched != null && StringUtils.hasText(matched.code())
                      ? matched.code()
                      : column.columnName(),
                  null,
                  // D3(v2.0 59):DWD 默认宽表——DIM 维表字段默认纳入(维度退化,可前端排除);
                  // 其他目标层维持原行为(维表默认不纳入,退化维度显式勾选)。
                  !ROLE_DIM.equals(binding.tableRole())
                      || DeriveLayerPolicy.DWD.equalsIgnoreCase(layer.code()),
                  null,
                  null,
                  authoritative || inherited != null ? null : suggestion,
                  null,
                  null));
          fieldCount++;
        }
        sourceViews.add(
            new SourceView(
                binding.id(), binding.datasourceId(), binding.sourceTable(), binding.tableRole(),
                binding.joinCondition(), odsModel.id(), odsModel.code(), odsTableName, true,
                fieldCount, null));
      }
    }
    for (TechnicalDef technical : TARGET_TECHNICALS.values()) {
      if (merged.containsKey(technical.name())) {
        continue;
      }
      merged.put(
          technical.name(),
          new ResolvedField(
              null, TARGET_ROLE, null, null, null, null, null, null, null, technical.name(),
              null, true, technical, null, null, null, null));
    }
    // 维表约定列(50):仅 DIM 目标;约定列是结构性字段,默认纳入但可改名/可取消。
    if (DeriveLayerPolicy.DIM.equalsIgnoreCase(layer.code())) {
      for (DimConventions.ConventionField convention :
          dimConventions.resolve(dialect, processCode, scdType)) {
        if (merged.containsKey(convention.name().toLowerCase(Locale.ROOT))) {
          continue;
        }
        merged.put(
            convention.name().toLowerCase(Locale.ROOT),
            new ResolvedField(
                null, TARGET_ROLE, null, null, null, convention.dataType(), null, null, null,
                convention.name(), null, true, null, null, null, convention, null));
      }
    }
    return new Resolution(
        sourceViews,
        new ArrayList<>(merged.values()),
        findExistingModel(processId, layer),
        warnings);
  }

  /**
   * 聚合/应用层解析(51/52):上游是**同过程的上游分层模型**(多选,默认全选),
   * 字段 = 所选上游模型字段的并集(同名先到先得),角色默认按标准字段角色推断。
   */
  private Resolution resolveAggregate(
      Long processId,
      WarehouseLayer layer,
      DeriveLayerPolicy.DeriveMode mode,
      String upstreamLayer,
      List<Long> upstreamModelIds,
      String processCode) {
    List<Model> candidates = modelRepository.listByProcessLayer(processId, upstreamLayer);
    List<String> warnings = new ArrayList<>();
    if (candidates.isEmpty()) {
      warnings.add(
          "该业务过程还没有 " + upstreamLayer + " 模型(上游缺失),请先按过程派生 " + upstreamLayer);
    }
    List<Model> selected =
        upstreamModelIds == null || upstreamModelIds.isEmpty()
            ? candidates
            : candidates.stream().filter(model -> upstreamModelIds.contains(model.id())).toList();
    if (!candidates.isEmpty() && selected.isEmpty()) {
      warnings.add("未勾选任何上游模型,请至少选择一个 " + upstreamLayer + " 模型");
    }
    List<StandardField> processFields = processApi.getFieldSets(processId);
    List<SourceView> sourceViews = new ArrayList<>();
    List<UpstreamModelView> upstreamViews = new ArrayList<>();
    Map<String, ResolvedField> merged = new LinkedHashMap<>();
    for (Model upstream : candidates) {
      StructureView structure = structureReader.publishedStructure(upstream.id());
      boolean isSelected = selected.contains(upstream);
      int fieldCount = 0;
      if (isSelected) {
        for (StructureView.ColumnView column : structure.columns()) {
          String key = column.columnName().toLowerCase(Locale.ROOT);
          if (INHERITANCE_EXCLUDED.contains(key)) {
            continue;
          }
          ResolvedField previous = merged.get(key);
          if (previous != null) {
            merged.put(key, previous.withConflict(upstream.code()));
            continue;
          }
          StandardField inherited = safeGetField(column.stdFieldId());
          StandardFieldMatcher.Match suggestion =
              inherited != null
                  ? null
                  : fieldMatcher.match(
                      column.columnName(), column.dataType(), column.comment(), processFields);
          boolean authoritative =
              suggestion != null && StandardFieldMatcher.isAuthoritative(suggestion.matchedBy());
          StandardField matched =
              inherited != null
                  ? inherited
                  : authoritative ? safeGetField(suggestion.stdFieldId()) : null;
          merged.put(
              key,
              new ResolvedField(
                  upstream.code(),
                  upstream.layerCode(),
                  upstream.id(),
                  upstream.code(),
                  column,
                  null,
                  matched != null ? matched.id() : null,
                  matched,
                  matched == null
                      ? null
                      : (inherited != null ? "inherited" : suggestion.matchedBy()),
                  matched != null && StringUtils.hasText(matched.code())
                      ? matched.code()
                      : column.columnName(),
                  null,
                  true,
                  null,
                  null,
                  authoritative || inherited != null ? null : suggestion,
                  null,
                  defaultAggregateSpec(matched)));
          fieldCount++;
        }
      }
      sourceViews.add(
          new SourceView(
              upstream.id(), null, upstream.code(), upstream.layerCode(), null, upstream.id(),
              upstream.code(), structure.tableName(), isSelected, fieldCount,
              isSelected ? null : "未勾选"));
      upstreamViews.add(
          new UpstreamModelView(
              upstream.id(), upstream.code(), upstream.name(), upstream.layerCode(), isSelected,
              structure.columns().size()));
    }
    for (TechnicalDef technical : TARGET_TECHNICALS.values()) {
      if (merged.containsKey(technical.name())) {
        continue;
      }
      merged.put(
          technical.name(),
          new ResolvedField(
              null, TARGET_ROLE, null, null, null, null, null, null, null, technical.name(),
              null, true, technical, null, null, null, null));
    }
    Resolution resolution =
        new Resolution(
            sourceViews, new ArrayList<>(merged.values()), findExistingModel(processId, layer),
            warnings);
    return resolution;
  }

  /** 默认字段角色:标准字段角色为 METRIC → 度量(SUM);其余(含未命中)默认维度(不擅自聚合)。 */
  private static AggregateSpec defaultAggregateSpec(StandardField stdField) {
    if (stdField != null && StandardField.ROLE_METRIC.equals(stdField.role())) {
      return new AggregateSpec(FIELD_ROLE_MEASURE, "SUM");
    }
    return new AggregateSpec(FIELD_ROLE_DIMENSION, null);
  }

  /**
   * 60/61:指标反推草稿——按指标中心反推上游模型与度量/维度建议(供派生页预填)。
   *
   * @param sourceLayer 数据来源(D6):空或 DWD=指标直接依赖的明细模型;DWS=该过程的 DWS 模型
   *     (ADS 复用已聚合结果)。选 DWD 时若指标引用的是 DWS 模型,原样返回该模型(不做降级)。
   */
  public MetricDraftView metricDraft(
      List<Long> metricIds, String dialect, String sourceLayer) {
    if (metricIds == null || metricIds.isEmpty()) {
      return MetricDraftView.empty();
    }
    MetricQueryApi query = metricQueryProvider.getIfAvailable();
    if (query == null) {
      return MetricDraftView.empty();
    }
    List<MetricQueryView> metrics = query.listEnabledByIds(metricIds);
    if (metrics.isEmpty()) {
      return MetricDraftView.empty();
    }
    List<Long> processIds =
        metrics.stream()
            .map(MetricQueryView::processId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    boolean fromAggregated =
        DeriveLayerPolicy.DWS.equalsIgnoreCase(
            sourceLayer == null ? "" : sourceLayer.trim());
    List<Long> upstreamModelIds =
        fromAggregated ? aggregatedModelIds(processIds) : referencedModelIds(metrics);
    List<String> warnings = new ArrayList<>();
    if (fromAggregated && upstreamModelIds.isEmpty()) {
      warnings.add(
          "所选指标的业务过程还没有 DWS 模型,请先把数据来源切回 DWD 或先派生 DWS");
    }
    if (!fromAggregated && upstreamModelIds.isEmpty()) {
      warnings.add("所选指标未绑定依赖模型,无法反推上游,请在指标中心补齐指标的模型引用");
    }
    String statPeriod = normalizeStatPeriod(
        metrics.stream()
            .map(MetricQueryView::statPeriod)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(null));
    List<DraftMeasure> measures = new ArrayList<>();
    Set<String> dimensions = new LinkedHashSet<>();
    for (MetricQueryView metric : metrics) {
      DraftMeasure measure = parseMeasure(metric);
      if (measure != null) {
        measures.add(measure);
      }
      dimensions.addAll(parseJsonStringArray(metric.statDimensions()));
    }
    return new MetricDraftView(
        processIds, upstreamModelIds, statPeriod, measures, List.copyOf(dimensions), warnings);
  }

  /** 指标依赖模型(DWD 明细取数):按指标 modelId 去重。 */
  private static List<Long> referencedModelIds(List<MetricQueryView> metrics) {
    return metrics.stream()
        .map(MetricQueryView::modelId)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .toList();
  }

  /** 该过程的 DWS 模型(ADS 复用已聚合结果)。 */
  private List<Long> aggregatedModelIds(List<Long> processIds) {
    Set<Long> ids = new LinkedHashSet<>();
    for (Long processId : processIds) {
      modelRepository.listByProcessLayer(processId, DeriveLayerPolicy.DWS).stream()
          .map(Model::id)
          .filter(java.util.Objects::nonNull)
          .forEach(ids::add);
    }
    return List.copyOf(ids);
  }

  /** 指标统计周期(DAY/WEEK/MONTH)→ 建模周期约定(1d/1w/1m);无法识别时交回用户选择。 */
  private static String normalizeStatPeriod(String metricPeriod) {
    if (!StringUtils.hasText(metricPeriod)) {
      return null;
    }
    return switch (metricPeriod.trim().toUpperCase(Locale.ROOT)) {
      case "HOUR", "1h" -> "1h";
      case "DAY", "1d" -> "1d";
      case "WEEK", "1w" -> "1w";
      case "MONTH", "1m" -> "1m";
      default -> null;
    };
  }

  /** 度量表达式解析:SUM(order_amount) / COUNT(DISTINCT order_id) / AVG(x) 等。 */
  private static DraftMeasure parseMeasure(MetricQueryView metric) {
    if (!StringUtils.hasText(metric.measureExpr())) {
      return null;
    }
    Matcher matcher = MEASURE_PATTERN.matcher(metric.measureExpr().trim());
    if (!matcher.matches()) {
      return null;
    }
    String rawFunc = matcher.group(1).toUpperCase(Locale.ROOT);
    boolean distinct = matcher.group(2) != null;
    String field = matcher.group(3);
    String func = rawFunc.equals("COUNT") && distinct ? "COUNT_DISTINCT" : rawFunc;
    if (!AGGREGATE_FUNCS.contains(func)) {
      func = "SUM";
    }
    return new DraftMeasure(field, func, func.toLowerCase(Locale.ROOT) + "_" + field.toLowerCase(Locale.ROOT));
  }

  /** JSON 字符串数组(如 ["order_date","order_city"]) → 元素列表;非 JSON 原样按逗号拆。 */
  private static List<String> parseJsonStringArray(String raw) {
    if (!StringUtils.hasText(raw)) {
      return List.of();
    }
    Matcher matcher = JSON_STRING_ARRAY_PATTERN.matcher(raw.trim());
    List<String> values = new ArrayList<>();
    while (matcher.find()) {
      values.add(matcher.group(1));
    }
    if (!values.isEmpty()) {
      return values;
    }
    return List.of(raw.split(",")).stream().map(String::trim).filter(StringUtils::hasText).toList();
  }

  private static final Pattern MEASURE_PATTERN =
      Pattern.compile("^([A-Za-z_]+)\\s*\\(\\s*(DISTINCT\\s+)?([A-Za-z0-9_.]+)\\s*\\)\\s*$");

  private static final Pattern JSON_STRING_ARRAY_PATTERN = Pattern.compile("\"([^\"]*)\"");

  /** 60:指标反推草稿视图(业务过程 + 上游模型 + 统计周期 + 度量/维度建议)。 */
  public record MetricDraftView(
      /** 所选指标的业务过程;多过程指标时前端按首个过程预填并提示。 */
      List<Long> processIds,
      List<Long> upstreamModelIds,
      String statPeriod,
      List<DraftMeasure> measures,
      List<String> dimensions,
      List<String> warnings) {

    public static MetricDraftView empty() {
      return new MetricDraftView(List.of(), List.of(), null, List.of(), List.of(), List.of());
    }
  }

  /** 一个度量建议:来源字段 + 聚合函数 + 落地字段名。 */
  public record DraftMeasure(String sourceColumn, String aggregateFunc, String landingName) {}

  /** ODS 模型定位:按来源绑定精确匹配且分层为 ODS(一表一模型)。 */
  private Model findOdsModel(ProcessApi.ProcessSourceView binding) {
    if (binding.datasourceId() == null || !StringUtils.hasText(binding.sourceTable())) {
      return null;
    }
    return modelRepository.listBySource(binding.datasourceId(), binding.sourceTable()).stream()
        .filter(model -> ODS_LAYER_CODE.equalsIgnoreCase(model.layerCode()))
        .findFirst()
        .orElse(null);
  }

  private StandardField safeGetField(Long stdFieldId) {
    if (stdFieldId == null) {
      return null;
    }
    try {
      return processApi.getField(stdFieldId);
    } catch (RuntimeException exception) {
      return null;
    }
  }

  /** 防重:同一业务过程 + 分层已有模型。 */
  private ExistingModel findExistingModel(Long processId, WarehouseLayer layer) {
    for (Long modelId : modelRepository.modelIdsByProcess(processId)) {
      Model model = modelRepository.findById(modelId).orElse(null);
      if (model != null && layer.code().equalsIgnoreCase(model.layerCode())) {
        return new ExistingModel(model.id(), model.code(), model.name());
      }
    }
    return null;
  }

  /**
   * 合并用户选择与继承信息:落地名/标准字段/转换表达式取用户值,列元数据取继承值;
   * 请求未携带的目标层技术列按该层规则补齐(技术列由分层规则拥有,不随勾选移除)。
   */
  private List<ResolvedField> mergeSelection(
      DeriveRequest request, Resolution resolution, DeriveLayerPolicy.DeriveMode mode) {
    Map<String, ResolvedField> inherited = new LinkedHashMap<>();
    for (ResolvedField field : resolution.fields()) {
      inherited.put(field.columnName().toLowerCase(Locale.ROOT), field);
    }
    List<ResolvedField> selection = new ArrayList<>();
    if (request.fields() != null) {
      for (DerivedField chosen : request.fields()) {
        if (!chosen.include() || !StringUtils.hasText(chosen.sourceColumn())) {
          continue;
        }
        ResolvedField base = inherited.get(chosen.sourceColumn().toLowerCase(Locale.ROOT));
        if (base == null) {
          throw new ModelingException(
              ModelingErrorCode.INVALID_COLUMN,
              "字段不在继承范围内：" + chosen.sourceTable() + "." + chosen.sourceColumn());
        }
        Long stdFieldId = chosen.stdFieldId() != null ? chosen.stdFieldId() : base.stdFieldId();
        StandardField stdField =
            stdFieldId == null
                ? null
                : java.util.Objects.equals(stdFieldId, base.stdFieldId())
                    ? base.stdField()
                    : safeGetField(stdFieldId);
        selection.add(
            new ResolvedField(
                base.sourceTable(), base.tableRole(), base.odsModelId(), base.odsTableName(),
                base.odsColumn(), base.overrideDataType(), stdFieldId, stdField, base.matchedBy(),
                StringUtils.hasText(chosen.landingField())
                    ? chosen.landingField().trim()
                    : base.landingField(),
                chosen.transformExpr(), true, base.technicalDef(), base.conflictWith(),
                base.suggestion(), base.convention(),
                resolveAggregateSpec(chosen, base, mode)));
      }
    }
    Set<String> present = new HashSet<>();
    selection.forEach(field -> present.add(field.columnName().toLowerCase(Locale.ROOT)));
    for (TechnicalDef technical : TARGET_TECHNICALS.values()) {
      if (present.contains(technical.name())) {
        continue;
      }
      selection.add(
          new ResolvedField(
              null, TARGET_ROLE, null, null, null, null, null, null, null, technical.name(),
              null, true, technical, null, null, null, null));
    }
    return selection;
  }

  /** 字段 → 列输入:类型按目标方言校验;标准字段关联优先,六类标准引用逐项回退到 ODS 列。 */
  private ModelingStructureApi.ColumnInput toColumnInput(ResolvedField field, ModelDialect dialect) {
    StandardField stdField = field.stdField();
    StructureView.ColumnView odsColumn = field.odsColumn();
    return new ModelingStructureApi.ColumnInput(
        field.landingField(),
        resolveDataType(field, dialect),
        field.length(),
        field.scale(),
        field.nullable() == null || field.nullable(),
        null,
        stdField != null && StringUtils.hasText(stdField.name())
            ? stdField.name()
            : field.comment(),
        stdField != null ? stdField.businessDesc() : null,
        ref(stdField == null ? null : stdField.stdTypeId(), odsColumn, ColumnRef.TYPE),
        // 标准字段没有命名标准引用(命名靠规则校验),命名标准沿用 ODS 列
        odsColumn == null ? null : odsColumn.stdNamingId(),
        stdField != null && stdField.stdCodeSetCode() != null
            ? stdField.stdCodeSetCode()
            : (odsColumn == null ? null : odsColumn.stdCodeSetCode()),
        ref(stdField == null ? null : stdField.stdUnitId(), odsColumn, ColumnRef.UNIT),
        ref(stdField == null ? null : stdField.stdCaliberId(), odsColumn, ColumnRef.CALIBER),
        ref(stdField == null ? null : stdField.stdSecurityId(), odsColumn, ColumnRef.SECURITY),
        field.stdFieldId(),
        field.aggregate() == null ? null : field.aggregate().fieldRole(),
        field.aggregate() == null ? null : field.aggregate().aggregateFunc(),
        field.transformExpr());
  }

  /** 六类标准引用:标准字段有值用标准字段,否则沿用 ODS 列上的引用(不丢治理成果)。 */
  private enum ColumnRef {
    TYPE,
    UNIT,
    CALIBER,
    SECURITY
  }

  private static Long ref(Long primary, StructureView.ColumnView odsColumn, ColumnRef kind) {
    if (primary != null || odsColumn == null) {
      return primary;
    }
    return switch (kind) {
      case TYPE -> odsColumn.stdTypeId();
      case UNIT -> odsColumn.stdUnitId();
      case CALIBER -> odsColumn.stdCaliberId();
      case SECURITY -> odsColumn.stdSecurityId();
    };
  }

  /**
   * 类型解析:目标方言目录包含该类型即原样使用;否则经类型标准(41 推荐 + 30 类型标准 std_type)
   * 做兼容映射;仍不可用则阻断并指明字段(不静默降级生成不可用 DDL)。
   */
  private String resolveDataType(ResolvedField field, ModelDialect dialect) {
    String dataType = field.dataType();
    if (!StringUtils.hasText(dataType)) {
      return null;
    }
    if (StructureDialectCatalog.lookupType(dialect, dataType) != null) {
      return dataType;
    }
    try {
      StandardRecommendApi.RecommendationReport report =
          recommendApi.recommend(
              new StandardRecommendApi.RecommendRequest(
                  field.columnName(), dataType, "UNKNOWN"));
      for (StandardRecommendApi.StandardCandidate candidate : report.typeCandidates()) {
        Standard standard = standardQueryApi.get(candidate.standardId());
        String mapped =
            standard == null || standard.fields() == null ? null : standard.fields().stdType();
        if (StringUtils.hasText(mapped)
            && StructureDialectCatalog.lookupType(dialect, mapped) != null) {
          return mapped;
        }
      }
    } catch (RuntimeException exception) {
      // 推荐不可用按不兼容处理,统一在下方阻断并提示。
    }
    throw new ModelingException(
        ModelingErrorCode.INVALID_COLUMN,
        "字段 " + field.landingField() + " 的类型 " + dataType + " 不在 " + dialect.name()
            + " 类型目录中，且类型标准未给出兼容映射，请先在语义中心补充类型标准");
  }

  /** 目标层分区:按分层 defaultPartition(空则不设分区)。 */
  private ModelingStructureApi.PartitionInput layerPartition(WarehouseLayer layer) {
    if (!StringUtils.hasText(layer.defaultPartition())) {
      return null;
    }
    return new ModelingStructureApi.PartitionInput(
        "LIST", List.of("event_time"), layer.defaultPartition());
  }

  /** 19 来源映射:源列可校验才写;不可校验(源表元数据取不到)计数跳过。 */
  private int writeSourceMappings(
      Long modelId, List<ResolvedField> selection, String operator) {
    Map<String, Set<String>> sourceColumns = new HashMap<>();
    int skipped = 0;
    for (ResolvedField field : selection) {
      if (field.odsModelId() == null) {
        continue;
      }
      Model odsModel = modelRepository.findById(field.odsModelId()).orElse(null);
      if (odsModel == null || odsModel.sourceDatasourceId() == null) {
        skipped++;
        continue;
      }
      Set<String> columns =
          sourceColumns.computeIfAbsent(
              odsModel.sourceDatasourceId() + "/" + odsModel.sourceTable(),
              ignored -> loadSourceColumns(odsModel));
      if (!columns.contains(field.columnName().toLowerCase(Locale.ROOT))) {
        skipped++;
        continue;
      }
      mappingService.setMapping(
          modelId,
          field.landingField(),
          odsModel.sourceDatasourceId(),
          odsModel.sourceDatabase(),
          odsModel.sourceTable(),
          field.columnName(),
          field.transformExpr(),
          field.stdFieldId(),
          operator);
    }
    return skipped;
  }

  private Set<String> loadSourceColumns(Model odsModel) {
    try {
      Set<String> names = new HashSet<>();
      catalogReader
          .listColumns(
              odsModel.sourceDatasourceId(), odsModel.sourceDatabase(), null,
              odsModel.sourceTable())
          .forEach(column -> names.add(column.name().toLowerCase(Locale.ROOT)));
      return names;
    } catch (RuntimeException exception) {
      return Set.of();
    }
  }

  /** 治理回填:本次已确定而 ODS 列上还空着的标准字段关联写回 ODS(治理沉淀在 ODS 层)。 */
  private int backfillOdsStdField(List<ResolvedField> selection, String operator) {
    int backfilled = 0;
    Map<Long, StructureView> structures = new HashMap<>();
    for (ResolvedField field : selection) {
      if (field.stdFieldId() == null || field.odsModelId() == null) {
        continue;
      }
      StructureView structure =
          structures.computeIfAbsent(field.odsModelId(), structureService::get);
      boolean alreadyLinked =
          structure.columns().stream()
              .anyMatch(
                  column ->
                      column.columnName().equalsIgnoreCase(field.columnName())
                          && column.stdFieldId() != null);
      if (alreadyLinked) {
        continue;
      }
      if (structureService.assignStandardField(
          field.odsModelId(), field.columnName(), field.stdFieldId(), operator)) {
        backfilled++;
      }
    }
    return backfilled;
  }

  private static FieldView toFieldView(ResolvedField field) {
    return new FieldView(
        field.sourceTable(),
        field.tableRole(),
        field.odsModelId(),
        field.columnName(),
        field.dataType(),
        field.length(),
        field.scale(),
        field.nullable(),
        field.comment(),
        field.stdFieldId(),
        field.stdField() == null ? null : field.stdField().code(),
        field.stdField() == null ? null : field.stdField().name(),
        field.matchedBy(),
        field.landingField(),
        field.include(),
        field.conflictWith() != null,
        field.conflictWith(),
        field.technical(),
        field.isConvention(),
        field.aggregate() == null ? null : field.aggregate().fieldRole(),
        field.aggregate() == null ? null : field.aggregate().aggregateFunc(),
        field.suggestion() == null ? null : field.suggestion().stdFieldId(),
        field.suggestion() == null ? null : field.suggestion().stdFieldName());
  }

  /**
   * 聚合层字段角色校验(51/52):角色只能是 DIMENSION/MEASURE;度量必须给出白名单内的聚合函数;
   * 维度不得携带聚合函数(归一为空)。非聚合层一律不带角色与函数。
   */
  private AggregateSpec resolveAggregateSpec(
      DerivedField chosen, ResolvedField base, DeriveLayerPolicy.DeriveMode mode) {
    if (!DeriveLayerPolicy.isAggregateMode(mode)) {
      return null;
    }
    // 目标层技术列与维表约定列不是聚合维度/度量(技术列进不了分组键,也不进主键)
    if (base.technical() || base.isConvention()) {
      return null;
    }
    String role =
        StringUtils.hasText(chosen.fieldRole())
            ? chosen.fieldRole().trim().toUpperCase(Locale.ROOT)
            : base.aggregate() == null ? FIELD_ROLE_DIMENSION : base.aggregate().fieldRole();
    if (!FIELD_ROLE_DIMENSION.equals(role) && !FIELD_ROLE_MEASURE.equals(role)) {
      throw new ModelingException(
          ModelingErrorCode.INVALID_COLUMN,
          "字段 " + base.landingField() + " 的角色必须为 DIMENSION(维度)或 MEASURE(度量)：" + role);
    }
    // 显式声明 MEASURE 时聚合函数必须由调用方给出(不静默回落默认);未显式声明时沿用字段默认。
    boolean explicitMeasure =
        StringUtils.hasText(chosen.fieldRole())
            && FIELD_ROLE_MEASURE.equals(chosen.fieldRole().trim().toUpperCase(Locale.ROOT));
    String func =
        StringUtils.hasText(chosen.aggregateFunc())
            ? chosen.aggregateFunc().trim().toUpperCase(Locale.ROOT)
            : explicitMeasure
                ? null
                : base.aggregate() == null ? null : base.aggregate().aggregateFunc();
    if (FIELD_ROLE_MEASURE.equals(role)) {
      if (!StringUtils.hasText(func)) {
        throw new ModelingException(
            ModelingErrorCode.INVALID_COLUMN,
            "度量字段 " + base.landingField() + " 必须指定聚合函数(SUM/COUNT/COUNT_DISTINCT/MAX/MIN/AVG)");
      }
      if (!AGGREGATE_FUNCS.contains(func)) {
        throw new ModelingException(
            ModelingErrorCode.INVALID_COLUMN,
            "聚合函数不合法：" + func + "(允许 " + AGGREGATE_FUNCS + ")");
      }
    } else {
      func = null;
    }
    return new AggregateSpec(role, func);
  }

  /**
   * 生效的上游分层(61):默认取分层能力矩阵的判定结果;仅 ADS(APPLICATION)可覆盖,
   * 用于"明细实时报表跳过 DWS、直接从 DWD 取数"。其余分层上游唯一,显式覆盖按非法请求阻断,
   * 避免前端传错参数时静默改变取数口径。
   */
  private static String effectiveUpstreamLayer(
      DeriveLayerPolicy.LayerSupport support, String override) {
    if (!StringUtils.hasText(override)) {
      return support.upstreamLayer();
    }
    String value = override.trim().toUpperCase(Locale.ROOT);
    boolean allowed =
        support.mode() == DeriveLayerPolicy.DeriveMode.APPLICATION
            && (DeriveLayerPolicy.DWS.equals(value) || DeriveLayerPolicy.DWD.equals(value));
    if (!allowed) {
      throw new ModelingException(
          ModelingErrorCode.INVALID_SEARCH,
          "数据来源仅 ADS 分层可选(DWS/DWD),当前分层上游固定为 " + support.upstreamLayer());
    }
    return value;
  }

  /** 统计周期校验(51):非空时必须在可选集合内。 */
  private void validateStatPeriod(String statPeriod) {
    if (StringUtils.hasText(statPeriod) && !STAT_PERIODS.contains(statPeriod.trim())) {
      throw new ModelingException(
          ModelingErrorCode.INVALID_SEARCH,
          "统计周期不合法：" + statPeriod + "(允许 " + STAT_PERIODS + ")");
    }
  }

  /** 命名建议(51/52):dws_<过程>_<周期> / ads_<应用>_<过程> / <层>_<过程>。 */
  private static String suggestedCode(
      WarehouseLayer layer,
      DeriveLayerPolicy.DeriveMode mode,
      String processCode,
      String statPeriod,
      String appCode) {
    String prefix = layer.code() == null ? "model" : layer.code().toLowerCase(Locale.ROOT);
    String process = StringUtils.hasText(processCode) ? processCode : "process";
    return switch (mode) {
      case AGGREGATE ->
          "dws_" + process + "_"
              + (StringUtils.hasText(statPeriod) ? statPeriod.trim() : "1d");
      case APPLICATION ->
          "ads_"
              + (StringUtils.hasText(appCode) ? appCode.trim().toLowerCase(Locale.ROOT) : "app")
              + "_" + process;
      case INHERIT -> prefix + "_" + process;
    };
  }

  /** 命名建议(模型名称):应用名>(层名)优先于 过程名(层名)。 */
  private static String suggestedName(String processCode, WarehouseLayer layer, String appName) {
    String layerName = layer.name() == null ? layer.code() : layer.name();
    if (StringUtils.hasText(appName)) {
      return appName.trim() + "（" + layerName + "）";
    }
    return (StringUtils.hasText(processCode) ? processCode : "模型") + "（" + layerName + "）";
  }

  private WarehouseLayer requireLayer(String layerCode) {
    WarehouseLayer layer = layerConfigApi.resolveByCode(layerCode);
    if (layer == null) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "目标分层不存在：" + layerCode);
    }
    return layer;
  }

  /** 业务过程编码(维表代理键命名来源:"dim_<过程编码>_sk");取不到时退化为 dim_sk。 */
  private String resolveProcessCode(Long processId) {
    try {
      return processApi.listProcesses(null).stream()
          .filter(process -> processId.equals(process.id()))
          .map(process -> process.code())
          .filter(code -> code != null && !code.isBlank())
          .findFirst()
          .orElse("");
    } catch (RuntimeException exception) {
      return "";
    }
  }

  private static int rate(int matched, int total) {
    return total == 0 ? 0 : (int) Math.round(matched * 100.0 / total);
  }
}
