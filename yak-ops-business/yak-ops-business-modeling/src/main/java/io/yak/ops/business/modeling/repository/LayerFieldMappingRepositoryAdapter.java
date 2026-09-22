package io.yak.ops.business.modeling.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingLayerFieldMappingMapper;
import io.yak.ops.common.bean.po.modeling.ModelingLayerFieldMappingPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for layer-field mappings (project-scoped). */
@Repository
@RequiredArgsConstructor
public class LayerFieldMappingRepositoryAdapter implements LayerFieldMappingRepository {

  private final ModelingLayerFieldMappingMapper mapper;
  private final CurrentProject currentProject;

  @Override
  public ModelingLayerFieldMappingPO upsert(
      ModelingLayerFieldMappingPO po, String operator) {
    Long projectId = currentProject.requireProjectId();
    po.setProjectId(projectId);
    ModelingLayerFieldMappingPO existing =
        mapper
            .selectList(
                new LambdaQueryWrapper<ModelingLayerFieldMappingPO>()
                    .eq(ModelingLayerFieldMappingPO::getProjectId, projectId)
                    .eq(ModelingLayerFieldMappingPO::getModelId, po.getModelId())
                    .eq(ModelingLayerFieldMappingPO::getProcessFieldId, po.getProcessFieldId())
                    .eq(ModelingLayerFieldMappingPO::getLayerId, po.getLayerId())
                    .eq(ModelingLayerFieldMappingPO::getLayerFieldName, po.getLayerFieldName()))
            .stream()
            .findFirst()
            .orElse(null);
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
    mapper.update(
        po, new LambdaQueryWrapper<ModelingLayerFieldMappingPO>().eq(ModelingLayerFieldMappingPO::getId, po.getId()));
    return po;
  }

  @Override
  public List<ModelingLayerFieldMappingPO> listByModel(Long modelId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(
        new LambdaQueryWrapper<ModelingLayerFieldMappingPO>()
            .eq(ModelingLayerFieldMappingPO::getProjectId, projectId)
            .eq(ModelingLayerFieldMappingPO::getModelId, modelId)
            .orderByAsc(ModelingLayerFieldMappingPO::getId));
  }

  @Override
  public List<ModelingLayerFieldMappingPO> listByProcessField(Long processFieldId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(
        new LambdaQueryWrapper<ModelingLayerFieldMappingPO>()
            .eq(ModelingLayerFieldMappingPO::getProjectId, projectId)
            .eq(ModelingLayerFieldMappingPO::getProcessFieldId, processFieldId)
            .orderByAsc(ModelingLayerFieldMappingPO::getLayerId)
            .orderByAsc(ModelingLayerFieldMappingPO::getId));
  }

  @Override
  public boolean existsBy(Long modelId, Long processFieldId, Long layerId, String layerFieldName) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<ModelingLayerFieldMappingPO>()
                .eq(ModelingLayerFieldMappingPO::getProjectId, projectId)
                .eq(ModelingLayerFieldMappingPO::getModelId, modelId)
                .eq(ModelingLayerFieldMappingPO::getProcessFieldId, processFieldId)
                .eq(ModelingLayerFieldMappingPO::getLayerId, layerId)
                .eq(ModelingLayerFieldMappingPO::getLayerFieldName, layerFieldName))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = currentProject.requireProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<ModelingLayerFieldMappingPO>()
                .eq(ModelingLayerFieldMappingPO::getProjectId, projectId)
                .eq(ModelingLayerFieldMappingPO::getId, id))
        > 0;
  }

}
