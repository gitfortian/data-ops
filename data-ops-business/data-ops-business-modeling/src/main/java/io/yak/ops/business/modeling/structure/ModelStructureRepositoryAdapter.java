package io.yak.ops.business.modeling.structure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelColumnMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelIndexMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import io.yak.ops.common.bean.po.modeling.ModelingModelColumnPO;
import io.yak.ops.common.bean.po.modeling.ModelingModelIndexPO;
import io.yak.ops.common.bean.po.modeling.ModelingModelPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * MyBatis adapter for model table structure. All operations are project-scoped
 * and restricted to live model rows.
 */
@Repository
public class ModelStructureRepositoryAdapter implements ModelStructureRepository {

  private final ModelingModelMapper modelMapper;
  private final ModelingModelColumnMapper columnMapper;
  private final ModelingModelIndexMapper indexMapper;
  private final CurrentProject currentProject;

  @Autowired
  public ModelStructureRepositoryAdapter(
      ModelingModelMapper modelMapper,
      ModelingModelColumnMapper columnMapper,
      ModelingModelIndexMapper indexMapper,
      CurrentProject currentProject) {
    this.modelMapper = modelMapper;
    this.columnMapper = columnMapper;
    this.indexMapper = indexMapper;
    this.currentProject = currentProject;
  }

  @Override
  public Optional<String> findTableName(Long modelId) {
    return findLiveModel(modelId).map(ModelingModelPO::getTableName);
  }

  @Override
  public Optional<String> findTableComment(Long modelId) {
    return findLiveModel(modelId).map(ModelingModelPO::getTableComment);
  }

  @Override
  public boolean updateTableInfo(Long modelId, String tableName, String tableComment) {
    Long projectId = requiredProjectId();
    return modelMapper.update(
            null,
            new LambdaUpdateWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getId, modelId)
                .eq(ModelingModelPO::getProjectId, projectId)
                .eq(ModelingModelPO::getDeleted, Boolean.FALSE)
                .set(ModelingModelPO::getTableName, tableName)
                .set(ModelingModelPO::getTableComment, tableComment)
                .set(ModelingModelPO::getUpdateTime, LocalDateTime.now()))
        > 0;
  }

  @Override
  public boolean updateTableAttributes(
      Long modelId,
      String primaryKeyJson,
      String partitionType,
      String partitionColumnsJson,
      String partitionExpr,
      String tablePropertiesJson) {
    Long projectId = requiredProjectId();
    return modelMapper.update(
            null,
            new LambdaUpdateWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getId, modelId)
                .eq(ModelingModelPO::getProjectId, projectId)
                .eq(ModelingModelPO::getDeleted, Boolean.FALSE)
                .set(ModelingModelPO::getPkColumns, primaryKeyJson)
                .set(ModelingModelPO::getPartitionType, partitionType)
                .set(ModelingModelPO::getPartitionColumns, partitionColumnsJson)
                .set(ModelingModelPO::getPartitionExpr, partitionExpr)
                .set(ModelingModelPO::getTableProperties, tablePropertiesJson)
                .set(ModelingModelPO::getUpdateTime, LocalDateTime.now()))
        > 0;
  }

  @Override
  public Optional<String> findPrimaryKeyJson(Long modelId) {
    return findLiveModel(modelId).map(ModelingModelPO::getPkColumns);
  }

  @Override
  public Optional<String> findPartitionType(Long modelId) {
    return findLiveModel(modelId).map(ModelingModelPO::getPartitionType);
  }

  @Override
  public Optional<String> findPartitionColumnsJson(Long modelId) {
    return findLiveModel(modelId).map(ModelingModelPO::getPartitionColumns);
  }

  @Override
  public Optional<String> findPartitionExpr(Long modelId) {
    return findLiveModel(modelId).map(ModelingModelPO::getPartitionExpr);
  }

  @Override
  public Optional<String> findTablePropertiesJson(Long modelId) {
    return findLiveModel(modelId).map(ModelingModelPO::getTableProperties);
  }

  @Override
  public List<ColumnDefinition> findColumns(Long modelId) {
    Long projectId = requiredProjectId();
    return columnMapper.selectList(
            new LambdaQueryWrapper<ModelingModelColumnPO>()
                .eq(ModelingModelColumnPO::getProjectId, projectId)
                .eq(ModelingModelColumnPO::getModelId, modelId)
                .orderByAsc(ModelingModelColumnPO::getSortOrder)
                .orderByAsc(ModelingModelColumnPO::getId))
        .stream()
        .map(ModelStructureRepositoryAdapter::toColumnDomain)
        .toList();
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void replaceColumns(Long modelId, List<ColumnDefinition> columns) {
    Long projectId = requiredProjectId();
    columnMapper.delete(
        new LambdaQueryWrapper<ModelingModelColumnPO>()
            .eq(ModelingModelColumnPO::getProjectId, projectId)
            .eq(ModelingModelColumnPO::getModelId, modelId));
    if (columns == null || columns.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    for (int index = 0; index < columns.size(); index++) {
      ColumnDefinition definition = columns.get(index);
      ModelingModelColumnPO po = new ModelingModelColumnPO();
      po.setProjectId(projectId);
      po.setModelId(modelId);
      po.setColumnName(definition.columnName());
      po.setDataType(definition.dataType());
      po.setLength(definition.length());
      po.setScale(definition.scale());
      po.setNullable(definition.nullable() == null || definition.nullable());
      po.setDefaultValue(definition.defaultValue());
      po.setColumnComment(definition.comment());
      po.setBusinessDescription(definition.businessDescription());
      po.setStdTypeId(definition.stdTypeId());
      po.setStdNamingId(definition.stdNamingId());
      po.setStdCodeSetCode(definition.stdCodeSetCode());
      po.setStdUnitId(definition.stdUnitId());
      po.setStdCaliberId(definition.stdCaliberId());
      po.setStdSecurityId(definition.stdSecurityId());
      po.setStdFieldId(definition.stdFieldId());
      po.setFieldRole(definition.fieldRole());
      po.setAggregateFunc(definition.aggregateFunc());
      po.setTransformExpr(definition.transformExpr());
      po.setSortOrder(index);
      po.setCreateTime(now);
      po.setUpdateTime(now);
      columnMapper.insert(po);
    }
  }

  @Override
  public boolean updateColumnStdField(Long modelId, String columnName, Long stdFieldId) {
    if (!StringUtils.hasText(columnName)) {
      return false;
    }
    Long projectId = requiredProjectId();
    return columnMapper.update(
            null,
            new LambdaUpdateWrapper<ModelingModelColumnPO>()
                .eq(ModelingModelColumnPO::getProjectId, projectId)
                .eq(ModelingModelColumnPO::getModelId, modelId)
                .eq(ModelingModelColumnPO::getColumnName, columnName.trim())
                .set(ModelingModelColumnPO::getStdFieldId, stdFieldId)
                .set(ModelingModelColumnPO::getUpdateTime, LocalDateTime.now()))
        > 0;
  }

  @Override
  public List<IndexDefinition> findIndexes(Long modelId) {
    Long projectId = requiredProjectId();
    return indexMapper.selectList(
            new LambdaQueryWrapper<ModelingModelIndexPO>()
                .eq(ModelingModelIndexPO::getProjectId, projectId)
                .eq(ModelingModelIndexPO::getModelId, modelId)
                .orderByAsc(ModelingModelIndexPO::getId))
        .stream()
        .map(ModelStructureRepositoryAdapter::toIndexDomain)
        .toList();
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void replaceIndexes(Long modelId, List<IndexDefinition> indexes) {
    Long projectId = requiredProjectId();
    indexMapper.delete(
        new LambdaQueryWrapper<ModelingModelIndexPO>()
            .eq(ModelingModelIndexPO::getProjectId, projectId)
            .eq(ModelingModelIndexPO::getModelId, modelId));
    if (indexes == null || indexes.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    for (IndexDefinition definition : indexes) {
      ModelingModelIndexPO po = new ModelingModelIndexPO();
      po.setProjectId(projectId);
      po.setModelId(modelId);
      po.setIndexName(definition.indexName());
      po.setUniqueIndex(definition.uniqueIndex() != null && definition.uniqueIndex());
      po.setIndexType(definition.indexType());
      po.setColumnNames(StructureJson.writeNameList(definition.columns()));
      po.setCreateTime(now);
      po.setUpdateTime(now);
      indexMapper.insert(po);
    }
  }

  private Optional<ModelingModelPO> findLiveModel(Long modelId) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
        modelMapper.selectOne(
            new LambdaQueryWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getId, modelId)
                .eq(ModelingModelPO::getProjectId, projectId)
                .eq(ModelingModelPO::getDeleted, Boolean.FALSE)));
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static ColumnDefinition toColumnDomain(ModelingModelColumnPO po) {
    return new ColumnDefinition(
        po.getId(),
        po.getColumnName(),
        po.getDataType(),
        po.getLength(),
        po.getScale(),
        po.getNullable(),
        po.getDefaultValue(),
        po.getColumnComment(),
        po.getBusinessDescription(),
        po.getSortOrder(),
        po.getStdTypeId(),
        po.getStdNamingId(),
        po.getStdCodeSetCode(),
        po.getStdUnitId(),
        po.getStdCaliberId(),
        po.getStdSecurityId(),
        po.getStdFieldId(),
        po.getFieldRole(),
        po.getAggregateFunc(),
        po.getTransformExpr());
  }

  private static IndexDefinition toIndexDomain(ModelingModelIndexPO po) {
    return new IndexDefinition(
        po.getId(),
        po.getIndexName(),
        po.getUniqueIndex(),
        po.getIndexType(),
        StructureJson.readNameList(po.getColumnNames()));
  }
}
