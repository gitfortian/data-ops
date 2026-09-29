package io.yak.ops.business.modeling.mapping;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.MappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureRepository;
import io.yak.ops.common.bean.po.modeling.ModelingColumnMappingPO;
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

  public MappingService(
      MappingRepository mappingRepository,
      ModelRepository modelRepository,
      ModelStructureRepository structureRepository,
      DataSourceCatalogReader catalogReader) {
    this.mappingRepository = mappingRepository;
    this.modelRepository = modelRepository;
    this.structureRepository = structureRepository;
    this.catalogReader = catalogReader;
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
      Long stdProcessFieldId) {}

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

  /** 设置/更新一条映射;源列经 catalog 门面校验存在。 */
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
    requireModel(modelId);
    requireTargetColumn(modelId, targetColumn);
    if (sourceDatasourceId == null
        || !StringUtils.hasText(sourceTable)
        || !StringUtils.hasText(sourceColumn)) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "来源映射必须提供数据源/源表/源字段");
    }
    String expressionError = TransformExpressionValidator.validate(transformExpr);
    if (expressionError != null) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, expressionError);
    }
    validateSourceColumnExists(sourceDatasourceId, sourceDatabase, sourceTable, sourceColumn);

    ModelingColumnMappingPO existing =
        mappingRepository.findByTargetColumn(modelId, targetColumn).orElse(null);
    ModelingColumnMappingPO po = existing == null ? new ModelingColumnMappingPO() : existing;
    po.setModelId(modelId);
    po.setTargetColumn(targetColumn);
    po.setSourceDatasourceId(sourceDatasourceId);
    po.setSourceDatabase(sourceDatabase);
    po.setSourceTable(sourceTable);
    po.setSourceColumn(sourceColumn);
    po.setTransformExpr(transformExpr);
    po.setStdProcessFieldId(stdProcessFieldId);
    mappingRepository.upsert(po, operator);
  }

  /** 清空单列映射。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public boolean clearMapping(Long modelId, String targetColumn) {
    requireModel(modelId);
    return mappingRepository.deleteByTargetColumn(modelId, targetColumn);
  }

  /** 批量清空模型全部映射。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int clearAll(Long modelId) {
    requireModel(modelId);
    return mappingRepository.deleteByModel(modelId);
  }

  /** 表达式语法校验(前端输入即时反馈)。 */
  public String validateExpression(String expression) {
    return TransformExpressionValidator.validate(expression);
  }

  private void validateSourceColumnExists(
      Long datasourceId, String database, String table, String sourceColumn) {
    List<CatalogColumn> columns = catalogReader.listColumns(datasourceId, database, null, table);
    boolean exists =
        columns.stream().anyMatch(column -> column.name().equalsIgnoreCase(sourceColumn));
    if (!exists) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "源字段不存在：" + sourceColumn);
    }
  }

  private void requireModel(Long modelId) {
    modelRepository
        .findById(modelId)
        .orElseThrow(
            () -> new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(modelId)));
  }

  private void requireTargetColumn(Long modelId, String targetColumn) {
    boolean exists =
        structureRepository.findColumns(modelId).stream()
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
        match.getStdProcessFieldId());
  }
}
