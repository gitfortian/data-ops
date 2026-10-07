package io.yak.ops.business.modeling.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingColumnMappingMapper;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for column source mappings (project-scoped). */
@Repository
@RequiredArgsConstructor
public class MappingRepositoryAdapter implements MappingRepository {

  private final ModelingColumnMappingMapper mapper;
  private final CurrentProject currentProject;

  @Override
  public ModelingColumnMappingPO upsert(ModelingColumnMappingPO po, String operator) {
    Long projectId = currentProject.requireProjectId();
    po.setProjectId(projectId);
    ModelingColumnMappingPO existing =
        mapper.selectOne(
            new LambdaQueryWrapper<ModelingColumnMappingPO>()
                .eq(ModelingColumnMappingPO::getProjectId, projectId)
                .eq(ModelingColumnMappingPO::getModelId, po.getModelId())
                .eq(ModelingColumnMappingPO::getTargetColumn, po.getTargetColumn()));
    if (existing == null) {
      po.setCreatedBy(operator);
      po.setCreateTime(LocalDateTime.now());
      po.setUpdateTime(LocalDateTime.now());
      mapper.insert(po);
      return po;
    }
    po.setId(existing.getId());
    po.setCreatedBy(existing.getCreatedBy());
    po.setCreateTime(existing.getCreateTime());
    po.setUpdateTime(LocalDateTime.now());
    mapper.update(po, new LambdaQueryWrapper<ModelingColumnMappingPO>().eq(ModelingColumnMappingPO::getId, po.getId()));
    return po;
  }

  @Override
  public List<ModelingColumnMappingPO> listByModel(Long modelId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(
        new LambdaQueryWrapper<ModelingColumnMappingPO>()
            .eq(ModelingColumnMappingPO::getProjectId, projectId)
            .eq(ModelingColumnMappingPO::getModelId, modelId)
            .orderByAsc(ModelingColumnMappingPO::getId));
  }

  @Override
  public Optional<ModelingColumnMappingPO> findByTargetColumn(Long modelId, String targetColumn) {
    Long projectId = currentProject.requireProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<ModelingColumnMappingPO>()
                .eq(ModelingColumnMappingPO::getProjectId, projectId)
                .eq(ModelingColumnMappingPO::getModelId, modelId)
                .eq(ModelingColumnMappingPO::getTargetColumn, targetColumn))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<ModelingColumnMappingPO> findByTargetColumnForUpdate(Long modelId, String targetColumn) {
    return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<ModelingColumnMappingPO>()
        .eq(ModelingColumnMappingPO::getProjectId, currentProject.requireProjectId())
        .eq(ModelingColumnMappingPO::getModelId, modelId)
        .eq(ModelingColumnMappingPO::getTargetColumn, targetColumn).last("FOR UPDATE")));
  }

  @Override
  public boolean deleteByTargetColumn(Long modelId, String targetColumn) {
    Long projectId = currentProject.requireProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<ModelingColumnMappingPO>()
                .eq(ModelingColumnMappingPO::getProjectId, projectId)
                .eq(ModelingColumnMappingPO::getModelId, modelId)
                .eq(ModelingColumnMappingPO::getTargetColumn, targetColumn))
        > 0;
  }

  @Override
  public List<ModelingColumnMappingPO> listBySource(
      Long datasourceId, String sourceDatabase, String sourceTable, String sourceColumn) {
    Long projectId = currentProject.requireProjectId();
    LambdaQueryWrapper<ModelingColumnMappingPO> wrapper =
        new LambdaQueryWrapper<ModelingColumnMappingPO>()
            .eq(ModelingColumnMappingPO::getProjectId, projectId)
            .eq(ModelingColumnMappingPO::getSourceDatasourceId, datasourceId);
    if (sourceDatabase != null && !sourceDatabase.isBlank()) {
      wrapper.eq(ModelingColumnMappingPO::getSourceDatabase, sourceDatabase);
    }
    if (sourceTable != null && !sourceTable.isBlank()) {
      wrapper.eq(ModelingColumnMappingPO::getSourceTable, sourceTable);
    }
    if (sourceColumn != null && !sourceColumn.isBlank()) {
      wrapper.eq(ModelingColumnMappingPO::getSourceColumn, sourceColumn);
    }
    return mapper.selectList(wrapper);
  }

  @Override
  public int deleteByModel(Long modelId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.delete(
        new LambdaQueryWrapper<ModelingColumnMappingPO>()
            .eq(ModelingColumnMappingPO::getProjectId, projectId)
            .eq(ModelingColumnMappingPO::getModelId, modelId));
  }
}
