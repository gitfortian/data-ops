package io.yak.ops.business.modeling.importer;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.modeling.api.ModelingStructureApi;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.MappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureFingerprint;
import io.yak.ops.common.constant.datasource.DataSourcePermissionCode;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.core.security.ActionAuthorization;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * First manual ODS source import: an existing empty DRAFT is bound to its real
 * source and populated with a physical structure and per-column source mappings
 * as ONE Modeling transaction. Does not edit already governed structures.
 */
@Service
@RequiredArgsConstructor
public class OdsManualImportCommitService {
  private static final int MAX_COLUMNS = 1000;

  private final ModelRepository models;
  private final MappingRepository mappings;
  private final ModelStructureService structures;
  private final DataSourceCatalogReader catalog;
  private final ActionAuthorization authorization;

  public record ColumnSelection(@NotBlank String targetColumn, @NotBlank String sourceColumn) {}

  /** Browser-confirmed physical-column identity, verified again against live catalog at commit. */
  public record SourceColumnSnapshot(String name, String typeName, Integer size,
                                     Integer scale, boolean nullable, boolean primaryKey) {}

  public record Request(@NotBlank String expectedDefinition,
                        @NotNull Long sourceDatasourceId,
                        String sourceDatabase,
                        @NotBlank String sourceTable,
                        @Valid @NotNull ModelingStructureApi.SaveStructureRequest structure,
                        @NotNull List<@Valid ColumnSelection> selections,
                        String sourceSchema,
                        List<@Valid SourceColumnSnapshot> expectedSourceColumns) {
    /** Schema-aware legacy constructor; new S1 flow sends expectedSourceColumns too. */
    public Request(String expectedDefinition, Long sourceDatasourceId, String sourceDatabase,
                   String sourceTable, ModelingStructureApi.SaveStructureRequest structure,
                   List<ColumnSelection> selections, String sourceSchema) {
      this(expectedDefinition, sourceDatasourceId, sourceDatabase, sourceTable,
          structure, selections, sourceSchema, null);
    }
    /** Historical client contract remains valid: default/no schema. */
    public Request(String expectedDefinition, Long sourceDatasourceId, String sourceDatabase,
                   String sourceTable, ModelingStructureApi.SaveStructureRequest structure,
                   List<ColumnSelection> selections) {
      this(expectedDefinition, sourceDatasourceId, sourceDatabase, sourceTable, structure, selections, null, null);
    }
  }

  public record Receipt(Long modelId, String status, Long sourceDatasourceId,
                        String sourceDatabase, String sourceTable, int sourceColumnCount,
                        int confirmedMappingCount, String newStructureDefinition) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Receipt commit(Long modelId, Request request, String operator) {
    authorization.requirePermission(ModelingPermissionCode.UPDATE);
    authorization.requirePermission(DataSourcePermissionCode.READ);
    if (request == null || request.sourceDatasourceId() == null || request.sourceDatasourceId() <= 0
        || !StringUtils.hasText(request.sourceTable())
        || request.expectedDefinition() == null || !request.expectedDefinition().matches("[0-9a-f]{64}")
        || request.structure() == null || request.structure().columns() == null) {
      throw reject("缺少来源、目标结构或有效编辑基线，请重新从授权源表预览");
    }

    Model model = models.findByIdForUpdate(modelId)
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND, "模型不存在或无权访问"));
    if (model.status() != ModelStatus.DRAFT || model.isRecycled()
        || !"ODS".equalsIgnoreCase(model.layerCode())) {
      throw reject("仅允许尚无字段的 ODS 草稿首次导入，禁止修改已发布或非 ODS 模型");
    }
    if (model.sourceDatasourceId() != null
        && (!Objects.equals(model.sourceDatasourceId(), request.sourceDatasourceId())
            || !Objects.equals(model.sourceDatabase(), request.sourceDatabase())
            || !Objects.equals(model.sourceTable(), request.sourceTable())
            || !Objects.equals(clean(model.sourceSchema()), clean(request.sourceSchema())))) {
      throw reject("目标模型已绑定其他来源，不允许重新绑定");
    }
    var original = structures.getForUpdate(modelId);
    if (!StructureFingerprint.of(original).equals(request.expectedDefinition())) {
      throw reject("物理草稿已被其他人修改，请刷新后重新审阅");
    }
    if (!original.columns().isEmpty() || !mappings.listByModel(modelId).isEmpty()) {
      throw reject("此模型已有结构或来源映射，请使用存量核验/修复流程，禁止覆盖");
    }

    // Recheck the physical owner AT COMMIT, not only when the browser navigated here.
    // A broad keyword match must never authorize a different database or schema.
    List<CatalogTable> sourceTableMatches = catalog.listTables(request.sourceDatasourceId(),
        request.sourceDatabase(), request.sourceSchema(), request.sourceTable()).stream()
        .filter(t -> Objects.equals(clean(t.database()), clean(request.sourceDatabase()))
            && Objects.equals(clean(t.schema()), clean(request.sourceSchema()))
            && Objects.equals(clean(t.name()), clean(request.sourceTable())))
        .toList();
    if (sourceTableMatches.size() != 1) {
      throw reject("来源表不可唯一核验；请返回来源目录重新确认");
    }
    boolean sourceClash = models.listBySource(request.sourceDatasourceId(), request.sourceTable())
        .stream().anyMatch(candidate -> !Objects.equals(candidate.id(), modelId)
            && "ODS".equalsIgnoreCase(candidate.layerCode())
            && Objects.equals(clean(candidate.sourceDatabase()), clean(request.sourceDatabase()))
            && (Objects.equals(clean(candidate.sourceSchema()), clean(request.sourceSchema()))
                || clean(candidate.sourceSchema()) == null && clean(request.sourceSchema()) != null));
    if (sourceClash) {
      throw reject("精确来源已存在 ODS 或有来源不完整的旧模型；必须先复用/核验，禁止重复绑定");
    }
    List<CatalogColumn> sourceColumns = catalog.listColumnsFresh(
        request.sourceDatasourceId(), request.sourceDatabase(), request.sourceSchema(), request.sourceTable());
    // Only the S1 preview→confirm journey provides this evidence. Revalidate the
    // acknowledged structure using the same DataSource catalog contract; drift
    // in type/width/nullability/PK is a review gate, not an implicit rewrite.
    if (request.expectedSourceColumns() != null) {
      Map<String, SourceColumnSnapshot> expected = new HashMap<>();
      for (SourceColumnSnapshot item : request.expectedSourceColumns()) {
        if (item == null || !StringUtils.hasText(item.name())
            || expected.putIfAbsent(key(item.name()), item) != null) {
          throw reject("预览源列身份缺失或重复，请重新核验");
        }
      }
      if (expected.size() != sourceColumns.size() || sourceColumns.stream().anyMatch(actual -> {
        SourceColumnSnapshot previous = expected.get(key(actual.name()));
        return previous == null
            || !Objects.equals(clean(previous.typeName()), clean(actual.typeName()))
            || !Objects.equals(previous.size(), actual.size())
            || !Objects.equals(previous.scale(), actual.scale())
            || previous.nullable() != actual.nullable()
            || previous.primaryKey() != actual.primaryKey();
      })) {
        throw reject("源表列结构已发生漂移（字段/类型/长度/可空/主键），请重新预览并人工确认");
      }
    }
    List<ModelingStructureApi.ColumnInput> targets = request.structure().columns();
    List<ColumnSelection> selected = request.selections();
    if (sourceColumns == null || sourceColumns.isEmpty()
        || sourceColumns.size() > MAX_COLUMNS || targets.isEmpty()
        || targets.size() > MAX_COLUMNS || selected == null
        || selected.size() > MAX_COLUMNS) {
      throw reject("来源或目标列为空、过多，或来源映射列表缺失");
    }
    Map<String, CatalogColumn> sources = new HashMap<>();
    for (CatalogColumn source : sourceColumns) {
      if (source == null || !StringUtils.hasText(source.name())
          || sources.putIfAbsent(key(source.name()), source) != null) {
        throw reject("来源列身份缺失或重复，不能可信导入");
      }
    }
    Map<String, ModelingStructureApi.ColumnInput> physical = new HashMap<>();
    for (var target : targets) {
      if (target == null || !StringUtils.hasText(target.columnName())
          || physical.putIfAbsent(key(target.columnName()), target) != null) {
        throw reject("物理列身份缺失或重复");
      }
    }
    Set<String> selectedSources = new HashSet<>();
    Set<String> selectedTargets = new HashSet<>();
    for (ColumnSelection pair : selected) {
      if (pair == null || !StringUtils.hasText(pair.sourceColumn())
          || !StringUtils.hasText(pair.targetColumn())
          || !sources.containsKey(key(pair.sourceColumn()))
          || !physical.containsKey(key(pair.targetColumn()))
          || !selectedSources.add(key(pair.sourceColumn()))
          || !selectedTargets.add(key(pair.targetColumn()))) {
        throw reject("来源映射必须引用当前源表和模型内的真实字段，且不能重复");
      }
    }
    // ODS designs may omit source columns or add their own columns. Persist only
    // the supplied real mappings; unmatched columns do not imply provenance.

    // Keep the row lock for the entire transaction. Structure service applies
    // its existing validation, optimistic evidence and audit policy.
    var saved = structures.saveEditContext(modelId, request.structure(), operator,
        request.expectedDefinition());
    if (model.sourceDatasourceId() == null && !(clean(request.sourceSchema()) == null
        ? models.assignSource(modelId, request.sourceDatasourceId(),
            request.sourceDatabase(), request.sourceTable(), operator)
        : models.assignSource(modelId, request.sourceDatasourceId(), request.sourceDatabase(),
            request.sourceSchema(), request.sourceTable(), operator))) {
      throw reject("模型来源身份保存失败，整批回滚");
    }
    if (!models.assignImportLineage(modelId, "SOURCE_TABLE", null, operator)) {
      throw reject("模型来源血缘标记失败，整批回滚");
    }
    for (ColumnSelection pair : selected) {
      ModelingColumnMappingPO mapping = new ModelingColumnMappingPO();
      mapping.setModelId(modelId);
      mapping.setTargetColumn(physical.get(key(pair.targetColumn())).columnName());
      mapping.setSourceDatasourceId(request.sourceDatasourceId());
      mapping.setSourceDatabase(request.sourceDatabase());
      mapping.setSourceSchema(clean(request.sourceSchema()));
      mapping.setSourceTable(request.sourceTable());
      mapping.setSourceColumn(sources.get(key(pair.sourceColumn())).name());
      mapping.setTransformExpr(null);
      mappings.upsert(mapping, operator);
    }
    List<ModelingColumnMappingPO> persisted = mappings.listByModel(modelId);
    if (persisted.size() != selected.size()) throw reject("来源映射回读数量不一致，事务回滚");
    for (ColumnSelection pair : selected) {
      boolean found = persisted.stream().anyMatch(row ->
          Objects.equals(row.getSourceDatasourceId(), request.sourceDatasourceId())
              && Objects.equals(row.getSourceDatabase(), request.sourceDatabase())
              && Objects.equals(clean(row.getSourceSchema()), clean(request.sourceSchema()))
              && Objects.equals(row.getSourceTable(), request.sourceTable())
              && row.getTransformExpr() == null
              && key(row.getTargetColumn()).equals(key(pair.targetColumn()))
              && key(row.getSourceColumn()).equals(key(pair.sourceColumn())));
      if (!found) throw reject("来源列映射落库回读不一致，事务回滚");
    }
    return new Receipt(modelId, "SAVED_AND_VERIFIED", request.sourceDatasourceId(),
        request.sourceDatabase(), request.sourceTable(), sources.size(),
        persisted.size(), saved.definition());
  }

  private static String clean(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private static String key(String column) { return column.trim().toLowerCase(Locale.ROOT); }

  private static ModelingException reject(String message) {
    return new ModelingException(ModelingErrorCode.INVALID_COLUMN, message);
  }
}
