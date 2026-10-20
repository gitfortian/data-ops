package io.yak.ops.business.modeling.mapping;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.MappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureRepository;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns column source-mapping rules (ticket 19): target column must exist on
 * the model, source column existence is validated through the datasource
 * public contract, transform expressions pass the syntax guard. Unmapped
 * columns are surfaced by pairing mappings with the live column list.
 */
@Component
public class MappingService {

  private final MappingRepository mappingRepository;
  private final ModelRepository modelRepository;
  private final ModelStructureRepository structureRepository;
  private final DataSourceCatalogReader catalogReader;
  private final io.yak.ops.core.security.ActionAuthorization authorization;

  public MappingService(
      MappingRepository mappingRepository,
      ModelRepository modelRepository,
      ModelStructureRepository structureRepository,
      DataSourceCatalogReader catalogReader, io.yak.ops.core.security.ActionAuthorization authorization) {
    this.mappingRepository = mappingRepository;
    this.modelRepository = modelRepository;
    this.structureRepository = structureRepository;
    this.catalogReader = catalogReader;
    this.authorization = authorization;
  }

  /** 视图项:目标列 + 映射(可空);mapped=false 即未映射标识。 */
  public record MappingView(
      String targetColumn,
      String dataType,
      boolean mapped,
      Long sourceDatasourceId,
      String sourceDatabase,
      String sourceTable,
      String sourceColumn,
      String transformExpr,
      Long stdProcessFieldId,
      String sourceSchema) {
    public MappingView(String targetColumn, String dataType, boolean mapped,
        Long sourceDatasourceId, String sourceDatabase, String sourceTable,
        String sourceColumn, String transformExpr, Long stdProcessFieldId) {
      this(targetColumn, dataType, mapped, sourceDatasourceId, sourceDatabase,
          sourceTable, sourceColumn, transformExpr, stdProcessFieldId, null);
    }
  }

  public List<MappingView> list(Long modelId) {
    requireModel(modelId);
    List<ColumnDefinition> definitions = structureRepository.findColumns(modelId);
    List<ModelingColumnMappingPO> mappings = mappingRepository.listByModel(modelId);
    List<MappingView> views = new ArrayList<>();
    for (ColumnDefinition definition : definitions) {
      views.add(
          toView(
              definition.columnName(),
              definition.dataType(),
              findMapping(mappings, definition.columnName())));
    }
    // 保留目标列已被删除的映射(孤儿行,列出以便清理)。
    for (ModelingColumnMappingPO po : mappings) {
      if (definitions.stream()
          .noneMatch(definition -> definition.columnName().equalsIgnoreCase(po.getTargetColumn()))) {
        views.add(toView(po.getTargetColumn(), null, po));
      }
    }
    return views;
  }

  /** 设置/更新一条映射；指定源字段时经 catalog 门面校验，纯表达式不要求来源。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void setMapping(
      Long modelId,
      String targetColumn,
      Long sourceDatasourceId,
      String sourceDatabase,
      String sourceTable,
      String sourceColumn,
      String transformExpr,
      String operator) {
    setMapping(
        modelId, targetColumn, sourceDatasourceId, sourceDatabase, sourceTable, sourceColumn,
        transformExpr, null, operator);
  }

  /** 设置/更新一条映射并写入标准字段关联(44 派生:来源映射携带 std_process_field_id)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void setMapping(
      Long modelId,
      String targetColumn,
      Long sourceDatasourceId,
      String sourceDatabase,
      String sourceTable,
      String sourceColumn,
      String transformExpr,
      Long stdProcessFieldId,
      String operator) {
    var model = requireModelForUpdate(modelId);
    requireTargetColumn(modelId, targetColumn);
    boolean expressionOnly = validateMappingInput(sourceDatasourceId, sourceDatabase,
        sourceTable, sourceColumn, transformExpr);
    if (!expressionOnly) {
      validateSourceColumnExists(sourceDatasourceId, sourceDatabase,
          schemaForSource(model, sourceDatasourceId, sourceDatabase, sourceTable),
          sourceTable, sourceColumn);
    }

    persistMapping(modelId, targetColumn, sourceDatasourceId, sourceDatabase, sourceTable, sourceColumn, transformExpr, stdProcessFieldId, operator);
  }

  private void persistMapping(Long modelId, String targetColumn, Long sourceDatasourceId, String sourceDatabase,
      String sourceTable, String sourceColumn, String transformExpr, Long stdProcessFieldId, String operator) {
    ModelingColumnMappingPO existing =
        mappingRepository.findByTargetColumn(modelId, targetColumn).orElse(null);
    ModelingColumnMappingPO po = existing == null ? new ModelingColumnMappingPO() : existing;
    po.setModelId(modelId);
    po.setTargetColumn(targetColumn);
    po.setSourceDatasourceId(sourceDatasourceId);
    po.setSourceDatabase(sourceDatasourceId == null ? null : sourceDatabase);
    po.setSourceSchema(sourceDatasourceId == null ? null
        : schemaForSource(requireModelForUpdate(modelId), sourceDatasourceId, sourceDatabase, sourceTable));
    po.setSourceTable(sourceDatasourceId == null ? null : sourceTable);
    po.setSourceColumn(sourceDatasourceId == null ? null : sourceColumn);
    po.setTransformExpr(transformExpr);
    po.setStdProcessFieldId(stdProcessFieldId);
    mappingRepository.upsert(po, operator);
  }

  /** 清空单列映射。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public boolean clearMapping(Long modelId, String targetColumn) {
    requireModelForUpdate(modelId);
    return mappingRepository.deleteByTargetColumn(modelId, targetColumn);
  }

  /** 批量清空模型全部映射。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int clearAll(Long modelId) {
    requireModelForUpdate(modelId);
    return mappingRepository.deleteByModel(modelId);
  }

  public record EditContext(String modelName, String dialect, ColumnDefinition column, MappingView mapping, String definition) {}

  /** Locks the parent first, then uses current reads for both children. */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public EditContext editContext(Long modelId, String targetColumn) {
    authorization.requirePermission(io.yak.ops.common.constant.modeling.ModelingPermissionCode.READ);
    var model = requireModelForUpdate(modelId);
    var column = structureRepository.findColumnsForUpdate(modelId).stream()
        .filter(c -> c.columnName().equalsIgnoreCase(targetColumn)).findFirst()
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.INVALID_COLUMN, "目标字段不存在，请刷新模型"));
    var mapping = toView(column.columnName(), column.dataType(),
        mappingRepository.findByTargetColumnForUpdate(modelId, column.columnName()).orElse(null));
    return new EditContext(model.name(), model.dialect().name(), column, mapping,
        MappingContextFingerprint.target(modelId, model.dialect().name(), column, mapping));
  }

  /** Existing mapping rules with an owner-controlled compare-and-save baseline. */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void saveIfCurrent(Long modelId, String targetColumn, Long datasourceId, String database,
      String table, String sourceColumn, String expression, String expectedDefinition, String operator) {
    authorization.requirePermission(io.yak.ops.common.constant.modeling.ModelingPermissionCode.UPDATE);
    var current = editContext(modelId, targetColumn);
    if (expectedDefinition == null || !expectedDefinition.matches("[0-9a-f]{64}")
        || !current.definition().equals(expectedDefinition)) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "目标字段或映射已变化，请刷新后重新核对");
    }
    boolean expressionOnly = validateMappingInput(datasourceId, database, table, sourceColumn, expression);
    if (!expressionOnly) {
      authorization.requirePermission(io.yak.ops.common.constant.datasource.DataSourcePermissionCode.READ);
      // External DDL is checked at this instant; expression-only mappings have no source to probe.
      var boundModel = requireModelForUpdate(modelId);
      String schema = schemaForSource(boundModel, datasourceId, database, table);
      if (catalogReader.listColumnsFresh(datasourceId, database, schema, table).stream()
          .noneMatch(c -> c.name().equalsIgnoreCase(sourceColumn))) {
        throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "源字段已变化，请重新选择");
      }
    }
    persistMapping(modelId, current.column().columnName(), datasourceId, database, table, sourceColumn,
        expression, current.mapping().stdProcessFieldId(), operator);
  }

  private io.yak.ops.business.modeling.domain.Model requireModelForUpdate(Long modelId) {
    return modelRepository.findByIdForUpdate(modelId)
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(modelId)));
  }

  private static boolean hasNoSource(Long datasourceId, String database, String table, String column) {
    return datasourceId == null && !StringUtils.hasText(database)
        && !StringUtils.hasText(table) && !StringUtils.hasText(column);
  }

  /** Derived from existing mapping content, not a second persisted mapping type. */
  static boolean isExpressionOnly(ModelingColumnMappingPO mapping) {
    return mapping != null && hasNoSource(mapping.getSourceDatasourceId(), mapping.getSourceDatabase(),
        mapping.getSourceTable(), mapping.getSourceColumn()) && !StringUtils.hasText(mapping.getSourceSchema())
        && StringUtils.hasText(mapping.getTransformExpr());
  }

  private static boolean validateMappingInput(Long datasourceId, String database,
      String table, String column, String expression) {
    boolean expressionOnly = hasNoSource(datasourceId, database, table, column);
    if (expressionOnly && !StringUtils.hasText(expression)) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "请选择源字段或填写表达式");
    }
    if (!expressionOnly) {
      if (datasourceId == null || datasourceId <= 0) {
        throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "源字段映射请选择数据源");
      }
      if (!StringUtils.hasText(database)) {
        throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "源字段映射请选择源库");
      }
      if (!StringUtils.hasText(table)) {
        throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "源字段映射请选择源表");
      }
      if (!StringUtils.hasText(column)) {
        throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "源字段映射请选择源字段");
      }
    }
    String error = TransformExpressionValidator.validate(expression);
    if (error != null) throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, error);
    return expressionOnly;
  }

  /** 表达式语法校验(前端输入即时反馈)。 */
  public String validateExpression(String expression) {
    return TransformExpressionValidator.validate(expression);
  }

  private void validateSourceColumnExists(
      Long datasourceId, String database, String schema, String table, String sourceColumn) {
    List<CatalogColumn> columns = catalogReader.listColumns(datasourceId, database, schema, table);
    boolean exists =
        columns.stream().anyMatch(column -> column.name().equalsIgnoreCase(sourceColumn));
    if (!exists) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "源字段不存在：" + sourceColumn);
    }
  }

  /** Named-schema ODS mappings can only be updated against the persisted same source. */
  private String schemaForSource(io.yak.ops.business.modeling.domain.Model model,
      Long datasourceId, String database, String table) {
    if (!StringUtils.hasText(model.sourceSchema())) return null;
    if (!java.util.Objects.equals(model.sourceDatasourceId(), datasourceId)
        || !java.util.Objects.equals(model.sourceDatabase(), database)
        || !java.util.Objects.equals(model.sourceTable(), table)) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN,
          "已有 ODS 来源带有 Schema 身份，不能将逐列映射悄悄改绑到其他来源");
    }
    return model.sourceSchema();
  }

  private void requireModel(Long modelId) {
    modelRepository
        .findById(modelId)
        .orElseThrow(
            () -> new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(modelId)));
  }

  private void requireTargetColumn(Long modelId, String targetColumn) {
    boolean exists =
        structureRepository.findColumnsForUpdate(modelId).stream()
            .anyMatch(definition -> definition.columnName().equalsIgnoreCase(targetColumn));
    if (!exists) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "目标字段不存在：" + targetColumn);
    }
  }

  private static ModelingColumnMappingPO findMapping(
      List<ModelingColumnMappingPO> mappings, String targetColumn) {
    return mappings.stream()
        .filter(po -> targetColumn.equalsIgnoreCase(po.getTargetColumn()))
        .findFirst()
        .orElse(null);
  }

  private static MappingView toView(
      String targetColumn, String dataType, ModelingColumnMappingPO match) {
    if (match == null) {
      return new MappingView(targetColumn, dataType, false, null, null, null, null, null, null);
    }
    return new MappingView(
        targetColumn,
        dataType,
        true,
        match.getSourceDatasourceId(),
        match.getSourceDatabase(),
        match.getSourceTable(),
        match.getSourceColumn(),
        match.getTransformExpr(),
        match.getStdProcessFieldId(),
        match.getSourceSchema());
  }
}
