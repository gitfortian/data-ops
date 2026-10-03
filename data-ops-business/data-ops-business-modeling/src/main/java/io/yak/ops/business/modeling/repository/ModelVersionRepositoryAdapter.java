package io.yak.ops.business.modeling.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelVersionMapper;
import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.domain.ModelVersionSummary;
import io.yak.ops.business.modeling.dao.model.ModelingModelVersionPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for model version snapshots. */
@Repository
public class ModelVersionRepositoryAdapter implements ModelVersionRepository {

  private final ModelingModelVersionMapper mapper;
  private final CurrentProject currentProject;

  @Autowired
  public ModelVersionRepositoryAdapter(ModelingModelVersionMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public ModelVersion insert(Long modelId, int versionNo, String structureJson,
                             String metaJson, int columnCount, String checksum,
                             String publishedBy) {
    Long projectId = currentProject.requireProjectId();
    LocalDateTime now = LocalDateTime.now();
    ModelingModelVersionPO po = new ModelingModelVersionPO();
    po.setProjectId(projectId);
    po.setModelId(modelId);
    po.setVersionNo(versionNo);
    po.setStructureJson(structureJson);
    po.setMetaJson(metaJson);
    po.setColumnCount(columnCount);
    po.setChecksum(checksum);
    po.setPublishedBy(publishedBy);
    po.setPublishTime(now);
    mapper.insert(po);
    return toDomain(po);
  }

  @Override
  public List<ModelVersionSummary> listByModelId(Long modelId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(
            new LambdaQueryWrapper<ModelingModelVersionPO>()
                .eq(ModelingModelVersionPO::getProjectId, projectId)
                .eq(ModelingModelVersionPO::getModelId, modelId)
                .orderByDesc(ModelingModelVersionPO::getVersionNo))
        .stream()
        .map(this::toSummary)
        .toList();
  }

  @Override
  public Optional<ModelVersion> findByVersionNo(Long modelId, int versionNo) {
    Long projectId = currentProject.requireProjectId();
    return Optional.ofNullable(
        mapper.selectOne(
            new LambdaQueryWrapper<ModelingModelVersionPO>()
                .eq(ModelingModelVersionPO::getProjectId, projectId)
                .eq(ModelingModelVersionPO::getModelId, modelId)
                .eq(ModelingModelVersionPO::getVersionNo, versionNo)))
        .map(this::toDomain);
  }

  @Override
  public Optional<ModelVersion> findById(Long versionId, Long modelId) {
    ModelingModelVersionPO po = mapper.selectById(versionId);
    if (po == null || !java.util.Objects.equals(po.getModelId(), modelId)) {
      return Optional.empty();
    }
    return Optional.of(toDomain(po));
  }

  @Override
  public Optional<ModelVersion> findLatestByModelId(Long modelId) {
    Long projectId = currentProject.requireProjectId();
    return Optional.ofNullable(
        mapper.selectOne(
            new LambdaQueryWrapper<ModelingModelVersionPO>()
                .eq(ModelingModelVersionPO::getProjectId, projectId)
                .eq(ModelingModelVersionPO::getModelId, modelId)
                .orderByDesc(ModelingModelVersionPO::getVersionNo)
                .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public int nextVersionNo(Long modelId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.nextVersionNo(projectId, modelId);
  }

  private ModelVersion toDomain(ModelingModelVersionPO po) {
    return new ModelVersion(
        po.getId(),
        po.getModelId(),
        po.getVersionNo(),
        po.getStructureJson(),
        po.getMetaJson(),
        po.getColumnCount() == null ? 0 : po.getColumnCount(),
        po.getChecksum(),
        po.getPublishedBy(),
        po.getPublishTime());
  }

  private ModelVersionSummary toSummary(ModelingModelVersionPO po) {
    return new ModelVersionSummary(
        po.getId(),
        po.getModelId(),
        po.getVersionNo(),
        po.getColumnCount() == null ? 0 : po.getColumnCount(),
        po.getChecksum(),
        po.getPublishedBy(),
        po.getPublishTime());
  }
}
