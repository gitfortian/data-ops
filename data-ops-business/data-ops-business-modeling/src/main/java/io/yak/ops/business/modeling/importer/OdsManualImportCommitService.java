package io.yak.ops.business.modeling.importer;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
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

  public record Request(@NotBlank String expectedDefinition,
                        @NotNull Long sourceDatasourceId,
                        String sourceDatabase,
                        @NotBlank String sourceTable,
                        @Valid @NotNull ModelingStructureApi.SaveStructureRequest structure,
                        @NotNull List<@Valid ColumnSelection> selections) {}

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
            || !Objects.equals(model.sourceTable(), request.sourceTable()))) {
      throw reject("目标模型已绑定其他来源，不允许重新绑定");
    }
    var original = structures.getForUpdate(modelId);
    if (!StructureFingerprint.of(original).equals(request.expectedDefinition())) {
      throw reject("物理草稿已被其他人修改，请刷新后重新审阅");
    }
    if (!original.columns().isEmpty() || !mappings.listByModel(modelId).isEmpty()) {
      throw reject("此模型已有结构或来源映射，请使用存量核验/修复流程，禁止覆盖");
    }

    List<CatalogColumn> sourceColumns = catalog.listColumnsFresh(
        request.sourceDatasourceId(), request.sourceDatabase(), null, request.sourceTable());
    List<ModelingStructureApi.ColumnInput> targets = request.structure().columns();
    List<ColumnSelection> selected = request.selections();
    if (sourceColumns == null || sourceColumns.isEmpty()
        || sourceColumns.size() > MAX_COLUMNS || targets.isEmpty()
        || targets.size() > MAX_COLUMNS || selected == null
        || selected.size() != sourceColumns.size()) {
      throw reject("ODS 镜像必须明确映射全部真实源列，列数为空、过多或不一致");
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
        throw reject("来源与物理目标必须为真实存在的一对一明确选择，禁止按名称推断");
      }
    }
    if (selectedSources.size() != sources.size()) {
      throw reject("存在未覆盖的真实源列，不能将结构导入冒充正式来源映射");
    }
    // Platform-generated columns are allowed ONLY when the real catalog
    // doesn't contain them; they must not be falsely represented as source.
    for (String targetName : physical.keySet()) {
      if (!selectedTargets.contains(targetName)
          && !("process_time".equals(targetName) || "event_time".equals(targetName))
          ) {
        throw reject("字段 " + targetName + " 没有真实源列映射，也不是平台技术字段");
      }
    }

    // Keep the row lock for the entire transaction. Structure service applies
    // its existing validation, optimistic evidence and audit policy.
    var saved = structures.saveEditContext(modelId, request.structure(), operator,
        request.expectedDefinition());
    if (model.sourceDatasourceId() == null && !models.assignSource(modelId,
        request.sourceDatasourceId(), request.sourceDatabase(), request.sourceTable(), operator)) {
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

  private static String key(String column) { return column.trim().toLowerCase(Locale.ROOT); }

  private static ModelingException reject(String message) {
    return new ModelingException(ModelingErrorCode.INVALID_COLUMN, message);
  }
}
