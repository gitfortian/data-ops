package io.yak.ops.business.modeling.importer;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.modeling.api.ModelingImportApi;
import io.yak.ops.business.modeling.api.ModelingStructureApi;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.exception.ModelingException;
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
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Reverse import (ticket 08 + 38): copies datasource tables into modeling models.
 * Metadata is read exclusively through the datasource public contract
 * (DataSourceCatalogReader); each table imports in its own transaction
 * (ReverseImportWriter) so one failure never blocks the rest of the batch and
 * never leaves a model without fields behind. Re-importing a table whose model
 * exists but carries no fields fills that model instead of skipping it.
 *
 * <p>ODS light governance (38/53): per column the standard field (35) is matched through the
 * shared {@link StandardFieldMatcher} (authoritative = exact/comment, stored on
 * {@code std_field_id}); the <b>type standard</b> is matched by field-name keyword through the
 * recommendation engine (never blindly tagging everything as 标识/id), the security standard is
 * annotated from the bound standard field. ODS only associates and annotates, it never renames,
 * retypes or masks (see ODS vs DWD governance contract).
 */
@Component
@Slf4j
public class ReverseImportService {

  private static final String DEFAULT_LAYER_CODE = "ODS";

  private final DataSourceCatalogReader catalogReader;
  private final ModelStructureService structureService;
  private final ModelRepository modelRepository;
  private final LayerConfigApi layerConfigApi;
  private final ProcessApi processApi;
  private final StandardFieldMatcher fieldMatcher;
  private final StandardQueryApi standardQueryApi;
  private final StandardRecommendApi recommendApi;
  private final ReverseImportWriter writer;

  public ReverseImportService(
      DataSourceCatalogReader catalogReader,
      ModelStructureService structureService,
      ModelRepository modelRepository,
      LayerConfigApi layerConfigApi,
      ProcessApi processApi,
      StandardFieldMatcher fieldMatcher,
      StandardQueryApi standardQueryApi,
      StandardRecommendApi recommendApi,
      ReverseImportWriter writer) {
    this.catalogReader = catalogReader;
    this.structureService = structureService;
    this.modelRepository = modelRepository;
    this.layerConfigApi = layerConfigApi;
    this.processApi = processApi;
    this.fieldMatcher = fieldMatcher;
    this.standardQueryApi = standardQueryApi;
    this.recommendApi = recommendApi;
    this.writer = writer;
  }

  /** 表浏览/搜索(透传 catalog 门面)。 */
  public List<CatalogTable> listTables(Long datasourceId, String keyword) {
    return catalogReader.listTables(datasourceId, null, null, keyword);
  }

  /** 单表列结构预览。 */
  public List<CatalogColumn> previewColumns(
      Long datasourceId, String database, String table) {
    return catalogReader.listColumns(datasourceId, database, null, table);
  }

  /**
   * 批量导入:每表独立事务;编码命中且已有字段时跳过,命中但无字段时按源表补全结构;
   * 单表失败记录后继续。
   */
  public ModelingImportApi.ImportResult importTables(
      ModelingImportApi.ImportRequest request, String operator) {
    List<String> created = new ArrayList<>();
    List<String> filled = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    List<ModelingImportApi.ImportResult.FailedImport> failed = new ArrayList<>();
    List<ModelingImportApi.ImportResult.ImportedModel> models = new ArrayList<>();
    int[] standardStats = new int[4];

    Map<String, WarehouseLayer> layers = new HashMap<>();
    WarehouseLayer layer = resolveLayer(request.layerCode(), layers);
    String layerCode = layer == null ? null : layer.code();
    String partitionExpr = layer == null ? null : layer.defaultPartition();
    List<StandardField> fieldLibrary = loadFieldLibrary();

    for (ModelingImportApi.ImportItem item : request.tables()) {
      String code = StringUtils.hasText(item.code()) ? item.code().trim() : item.table();
      try {
        List<CatalogColumn> columns =
            catalogReader.listColumns(item.datasourceId(), item.database(), null, item.table());
        if (columns.isEmpty()) {
          throw new ModelingException(ModelingErrorCode.CREATE_FAILED, "表 " + item.table() + " 无列元数据");
        }
        AppliedColumns applied = buildColumns(columns, fieldLibrary, item.eventTimeField(), standardStats);
        List<ModelingImportApi.ImportResult.ColumnMatch> fieldMatches = applied.matches();

        Optional<Model> existing = modelRepository.findByCode(code);
        if (existing.isPresent()) {
          Model model = existing.get();
          // Model code collision does not authorize rebinding an existing model.
          if (model.sourceDatasourceId() != null
              && (!model.sourceDatasourceId().equals(item.datasourceId())
                  || (StringUtils.hasText(model.sourceDatabase())
                      && !model.sourceDatabase().equals(item.database()))
                  || (StringUtils.hasText(model.sourceTable())
                      && !model.sourceTable().equals(item.table())))) {
            throw new ModelingException(ModelingErrorCode.INVALID_COLUMN,
                "模型编码已存在且绑定不同来源，必须人工核对");
          }
          StructureView current = structureService.get(model.id());
          if (!current.columns().isEmpty()) {
            // SKIPPED means no structure/source/mapping mutation. Legacy source repair
            // requires an explicit review and must not be inferred from a code match.
            skipped.add(item.table());
            models.add(
                new ModelingImportApi.ImportResult.ImportedModel(
                    item.table(), model.id(),
                    ModelingImportApi.ImportResult.ImportAction.SKIPPED, List.of()));
            continue;
          }
          // 已有空模型:按源表补全结构;分层/表名/来源沿用既有值,不覆盖人工配置。
          WarehouseLayer effective =
              model.layerCode() == null ? layer : resolveLayer(model.layerCode(), layers);
          writer.fillStructure(
              model.id(),
              plan(
                  model.name(), code, model.dialect().name(),
                  StringUtils.hasText(model.description()) ? model.description() : item.remarks(),
                  null,
                  StringUtils.hasText(current.tableName()) ? current.tableName() : item.table(),
                  model.layerCode() == null ? layerCode : null,
                  effective == null ? null : effective.defaultPartition(),
                  item.datasourceId(), item.database(), item.table(),
                  applied.inputs(), applied.primaryKey(),
                  columns.stream().map(CatalogColumn::name).toList()),
              operator);
          filled.add(item.table());
          models.add(
              new ModelingImportApi.ImportResult.ImportedModel(
                  item.table(), model.id(),
                  ModelingImportApi.ImportResult.ImportAction.FILLED, fieldMatches));
          continue;
        }

        String name =
            StringUtils.hasText(item.name())
                ? item.name().trim()
                : StringUtils.hasText(item.remarks()) ? item.remarks() : item.table();
        Model model =
            writer.createWithStructure(
                plan(
                    name, code, request.dialect(), item.remarks(), request.directoryId(),
                    item.table(), layerCode, partitionExpr,
                    item.datasourceId(), item.database(), item.table(),
                    applied.inputs(), applied.primaryKey(),
                  columns.stream().map(CatalogColumn::name).toList()),
                operator);
        created.add(item.table());
        models.add(
            new ModelingImportApi.ImportResult.ImportedModel(
                item.table(), model.id(),
                ModelingImportApi.ImportResult.ImportAction.CREATED, fieldMatches));
      } catch (RuntimeException exception) {
        log.warn("reverse import failed for table {}: {}", item.table(), exception.getMessage());
        failed.add(
            new ModelingImportApi.ImportResult.FailedImport(
                item.table(), exception.getMessage()));
        models.add(
            new ModelingImportApi.ImportResult.ImportedModel(
                item.table(), null, ModelingImportApi.ImportResult.ImportAction.FAILED,
                List.of()));
      }
    }
    return new ModelingImportApi.ImportResult(
        created,
        filled,
        skipped,
        failed,
        models,
        new ModelingImportApi.ImportResult.StandardApplyStats(
            standardStats[0], standardStats[1], standardStats[2], standardStats[3]));
  }

  /** 标准字段库(35):逐列匹配的来源;不可用时降级为空(只是全部列未命中)。 */
  private List<StandardField> loadFieldLibrary() {
    try {
      return processApi.listFields(null);
    } catch (RuntimeException exception) {
      log.warn("standard field library unavailable, columns stay unmatched: {}",
          exception.getMessage());
      return List.of();
    }
  }

  private static ReverseImportPlan plan(
      String name,
      String code,
      String dialect,
      String description,
      Long directoryId,
      String tableName,
      String layerCode,
      String partitionExpr,
      Long sourceDatasourceId,
      String sourceDatabase,
      String sourceTable,
      List<ModelingStructureApi.ColumnInput> columns,
      List<String> primaryKey,
      List<String> sourceColumnNames) {
    return new ReverseImportPlan(
        name, code, dialect, description, directoryId, tableName, layerCode, partitionExpr,
        sourceDatasourceId, sourceDatabase, sourceTable, columns, primaryKey, sourceColumnNames);
  }

  /**
   * 源表列 → 模型字段:标准引用来自「字段名 → 标准字段库匹配 → 标准字段绑定的标准」(53 修正:
   * 不直接匹配类型标准,避免所有列都被默认套成"标识")。未匹配到标准字段的列不套用任何标准
   * (未治理,标 warning 交用户沉淀/关联),随后追加技术字段
   * (process_time 数据处理时间 + event_time 业务事件时间)。
   *
   * <p>ODS 轻治理(38/57):只落 标准字段关联 + 类型/安全标准**标注**(来自标准字段绑定,不脱敏
   * 不改值);码值/单位/口径/命名在 ODS 不写入(保留源貌,留待 DWD 重治理)。
   */
  private AppliedColumns buildColumns(
      List<CatalogColumn> columns, List<StandardField> fieldLibrary,
      String eventTimeField, int[] standardStats) {
    List<ModelingStructureApi.ColumnInput> inputs = new ArrayList<>();
    List<ModelingImportApi.ImportResult.ColumnMatch> matches = new ArrayList<>();
    for (CatalogColumn column : columns) {
      ModelingStructureApi.ColumnInput raw = toColumnInput(column);
      StandardFieldMatcher.Match match =
          fieldMatcher.match(column.name(), column.typeName(), column.remarks(), fieldLibrary);
      // 只有精确/注释匹配才落关联;名称相似仅作为建议(见 StandardFieldMatcher.isAuthoritative)。
      boolean linked = match != null && StandardFieldMatcher.isAuthoritative(match.matchedBy());
      StandardField boundField = linked ? findByFieldId(fieldLibrary, match.stdFieldId()) : null;
      // 53(用户裁定修订):类型标准按字段名关键词匹配(推荐引擎已修好,不再盲目套"标识");
      // 标准字段关联仍绑定驱动;安全标注仍来自标准字段绑定。
      Long stdTypeId = recommendTypeId(column.name(), column.typeName());
      Long stdSecurityId = boundField == null ? null : boundField.stdSecurityId();
      List<String> missingStandards = new ArrayList<>();
      if (stdTypeId != null) {
        standardStats[0]++;
      } else {
        missingStandards.add("类型标准");
      }
      if (stdSecurityId != null) {
        standardStats[1]++;
      } else {
        missingStandards.add("安全标准");
      }
      if (stdTypeId == null && stdSecurityId == null) {
        standardStats[3]++;
      }
      inputs.add(
          new ModelingStructureApi.ColumnInput(
              raw.columnName(), raw.dataType(), raw.length(), raw.scale(),
              raw.nullable(), raw.defaultValue(), raw.comment(),
              raw.businessDescription(), stdTypeId, null, null, null, null, stdSecurityId,
              linked ? match.stdFieldId() : null, null, null, null));
      matches.add(
          new ModelingImportApi.ImportResult.ColumnMatch(
              column.name(),
              column.typeName(),
              linked ? match.stdFieldId() : null,
              linked ? match.stdFieldCode() : null,
              linked ? match.stdFieldName() : null,
              match == null ? null : match.matchedBy(),
              missingStandards.isEmpty() ? null : String.join("、", missingStandards) + "未命中，保留原值",
              false,
              linked ? null : (match == null ? null : match.stdFieldId()),
              linked ? null : (match == null ? null : match.stdFieldName()),
              stdTypeId,
              resolveTypeLabel(stdTypeId)));
    }
    // 技术字段:process_time(数据处理时间) + event_time(业务事件时间)。
    addTechnical(inputs, matches, "process_time", "DATETIME", null, "数据处理时间");
    addTechnical(inputs, matches, "event_time", "DATETIME", null, "业务事件时间");
    return new AppliedColumns(inputs, primaryKeys(columns), matches);
  }

  /** 追加一个技术列(已存在同名源列则跳过;技术列不参与治理,审阅标"技术列")。 */
  private static void addTechnical(
      List<ModelingStructureApi.ColumnInput> inputs,
      List<ModelingImportApi.ImportResult.ColumnMatch> matches,
      String name, String type, String length, String comment) {
    if (containsColumn(inputs, name)) {
      return;
    }
    inputs.add(
        new ModelingStructureApi.ColumnInput(
            name, type, length == null ? null : Integer.valueOf(length),
            null, Boolean.TRUE, null, comment, null));
    matches.add(
        new ModelingImportApi.ImportResult.ColumnMatch(
            name, type, null, null, null, null, null, true, null, null, null, null));
  }

  /**
   * event_time 的来源业务字段:优先用用户指定(需在源列中);否则自动识别源表里第一个
   * 含 time 的列,其次含 date/dt 的列;都没有则返回 null(event_time 仅留标注位)。
   */
  private static String resolveEventTimeSource(
      List<CatalogColumn> columns, String eventTimeField) {
    if (StringUtils.hasText(eventTimeField)) {
      String override = eventTimeField.trim();
      if (columns.stream().anyMatch(column -> override.equalsIgnoreCase(column.name()))) {
        return override;
      }
    }
    return columns.stream()
        .map(CatalogColumn::name)
        .filter(name -> name.toLowerCase(Locale.ROOT).contains("time"))
        .findFirst()
        .orElseGet(
            () ->
                columns.stream()
                    .map(CatalogColumn::name)
                    .filter(
                        name ->
                            name.toLowerCase(Locale.ROOT).contains("date")
                                || "dt".equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null));
  }

  private static boolean containsColumn(
      List<ModelingStructureApi.ColumnInput> inputs, String name) {
    return inputs.stream().anyMatch(input -> name.equalsIgnoreCase(input.columnName()));
  }

  /** 按标准字段 ID 在库中定位(53:绑定来源);缺失返回 null 即按未治理处理。 */
  private static StandardField findByFieldId(List<StandardField> fields, Long fieldId) {
    if (fields == null || fieldId == null) {
      return null;
    }
    return fields.stream().filter(field -> fieldId.equals(field.id())).findFirst().orElse(null);
  }

  /**
   * 类型标准(53 用户裁定):按字段名关键词匹配,取推荐引擎类型候选首位;
   * 候选为空 = 未命中,不套用(返回 null,标 warning)。推荐失败降级不阻断导入。
   */
  private Long recommendTypeId(String fieldName, String dataType) {
    try {
      StandardRecommendApi.RecommendationReport report =
          recommendApi.recommend(
              new StandardRecommendApi.RecommendRequest(fieldName, dataType, "UNKNOWN"));
      return report.typeCandidates().isEmpty()
          ? null
          : report.typeCandidates().get(0).standardId();
    } catch (RuntimeException exception) {
      log.warn("type recommendation degraded for {}: {}", fieldName, exception.getMessage());
      return null;
    }
  }

  /** 类型标准 ID → 名称（编码）标签(审阅展示);解析失败降级为 null,不阻断导入。 */
  private String resolveTypeLabel(Long stdTypeId) {
    if (stdTypeId == null) {
      return null;
    }
    try {
      return standardQueryApi.labels(List.of(stdTypeId)).get(stdTypeId);
    } catch (RuntimeException exception) {
      log.warn("type standard label resolve degraded for {}: {}", stdTypeId, exception.getMessage());
      return null;
    }
  }

  private static List<String> primaryKeys(List<CatalogColumn> columns) {
    return columns.stream()
        .filter(CatalogColumn::primaryKey)
        .map(CatalogColumn::name)
        .toList();
  }

  /** 目标分层定位(38):defaultPartition 预填;分层不存在/停用时降级不阻断;同一分层只查一次。 */
  private WarehouseLayer resolveLayer(String layerCode, Map<String, WarehouseLayer> cache) {
    String code = StringUtils.hasText(layerCode) ? layerCode : DEFAULT_LAYER_CODE;
    if (cache.containsKey(code)) {
      return cache.get(code);
    }
    WarehouseLayer layer;
    try {
      layer = layerConfigApi.resolveByCode(code);
    } catch (RuntimeException exception) {
      log.warn("layer locate degraded for {}: {}", code, exception.getMessage());
      layer = null;
    }
    cache.put(code, layer);
    return layer;
  }

  private static ModelingStructureApi.ColumnInput toColumnInput(CatalogColumn column) {
    return new ModelingStructureApi.ColumnInput(
        sanitizeIdentifier(column.name()),
        column.typeName(),
        column.size() != null && column.size() > 0 ? column.size() : null,
        column.scale() != null && column.scale() > 0 ? column.scale() : null,
        column.nullable(),
        null,
        column.remarks(),
        null);
  }

  /** 列名清洗兜底:去空白、非法字符转下划线、非法首字符补前缀。 */
  private static String sanitizeIdentifier(String name) {
    String trimmed = name == null ? "" : name.trim();
    if (trimmed.isEmpty()) {
      return trimmed;
    }
    String candidate = trimmed.replaceAll("[^A-Za-z0-9_$]", "_");
    if (!candidate.matches("^[A-Za-z0-9_].*")) {
      candidate = "_" + candidate;
    }
    return candidate;
  }

  /** 一列的解析结果:写入用 inputs + 主键 + 逐列治理明细。 */
  private record AppliedColumns(
      List<ModelingStructureApi.ColumnInput> inputs,
      List<String> primaryKey,
      List<ModelingImportApi.ImportResult.ColumnMatch> matches) {}
}
