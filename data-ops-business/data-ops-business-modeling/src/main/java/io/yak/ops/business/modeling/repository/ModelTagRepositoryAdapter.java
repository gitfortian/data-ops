package io.yak.ops.business.modeling.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelTagRelMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingTagMapper;
import io.yak.ops.business.modeling.domain.ModelingTag;
import io.yak.ops.business.modeling.dao.model.ModelingModelTagRelPO;
import io.yak.ops.business.modeling.dao.model.ModelingTagPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** MyBatis adapter for modeling tags and model-tag relations. */
@Repository
public class ModelTagRepositoryAdapter implements ModelTagRepository {

  private final ModelingTagMapper tagMapper;
  private final ModelingModelTagRelMapper relMapper;
  private final CurrentProject currentProject;

  @Autowired
  public ModelTagRepositoryAdapter(
      ModelingTagMapper tagMapper,
      ModelingModelTagRelMapper relMapper,
      CurrentProject currentProject) {
    this.tagMapper = tagMapper;
    this.relMapper = relMapper;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused tests; project-scoped operations will fail closed. */
  public ModelTagRepositoryAdapter(ModelingTagMapper tagMapper, ModelingModelTagRelMapper relMapper) {
    this(tagMapper, relMapper, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public ModelingTag insert(String name) {
    Long projectId = requiredProjectId();
    ModelingTagPO po = new ModelingTagPO();
    po.setProjectId(projectId);
    po.setName(name);
    po.setCreateTime(LocalDateTime.now());
    tagMapper.insert(po);
    return new ModelingTag(po.getId(), po.getName(), po.getCreateTime());
  }

  @Override
  public Optional<ModelingTag> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            tagMapper.selectOne(
                new LambdaQueryWrapper<ModelingTagPO>()
                    .eq(ModelingTagPO::getId, id)
                    .eq(ModelingTagPO::getProjectId, projectId)))
        .map(ModelTagRepositoryAdapter::toDomain);
  }

  @Override
  public boolean existsByName(String name) {
    Long projectId = requiredProjectId();
    return tagMapper.selectCount(
            new LambdaQueryWrapper<ModelingTagPO>()
                .eq(ModelingTagPO::getProjectId, projectId)
                .eq(ModelingTagPO::getName, name))
        > 0L;
  }

  @Override
  public List<ModelingTag> listAll() {
    Long projectId = requiredProjectId();
    return tagMapper.selectList(
            new LambdaQueryWrapper<ModelingTagPO>()
                .eq(ModelingTagPO::getProjectId, projectId)
                .orderByAsc(ModelingTagPO::getName)
                .orderByAsc(ModelingTagPO::getId))
        .stream()
        .map(ModelTagRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    relMapper.delete(
        new LambdaQueryWrapper<ModelingModelTagRelPO>()
            .eq(ModelingModelTagRelPO::getProjectId, projectId)
            .eq(ModelingModelTagRelPO::getTagId, id));
    return tagMapper.delete(
            new LambdaQueryWrapper<ModelingTagPO>()
                .eq(ModelingTagPO::getId, id)
                .eq(ModelingTagPO::getProjectId, projectId))
        > 0;
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void replaceModelTags(Long modelId, List<Long> tagIds) {
    Long projectId = requiredProjectId();
    relMapper.delete(
        new LambdaQueryWrapper<ModelingModelTagRelPO>()
            .eq(ModelingModelTagRelPO::getProjectId, projectId)
            .eq(ModelingModelTagRelPO::getModelId, modelId));
    if (tagIds == null) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    tagIds.stream().distinct().forEach(tagId -> {
      ModelingModelTagRelPO rel = new ModelingModelTagRelPO();
      rel.setProjectId(projectId);
      rel.setModelId(modelId);
      rel.setTagId(tagId);
      rel.setCreateTime(now);
      relMapper.insert(rel);
    });
  }

  @Override
  public List<Long> tagIdsForModel(Long modelId) {
    Long projectId = requiredProjectId();
    return relMapper.selectList(
            new LambdaQueryWrapper<ModelingModelTagRelPO>()
                .eq(ModelingModelTagRelPO::getProjectId, projectId)
                .eq(ModelingModelTagRelPO::getModelId, modelId))
        .stream()
        .map(ModelingModelTagRelPO::getTagId)
        .toList();
  }

  @Override
  public Map<Long, List<Long>> tagIdsForModels(List<Long> modelIds) {
    Long projectId = requiredProjectId();
    if (modelIds == null || modelIds.isEmpty()) {
      return Map.of();
    }
    return relMapper.selectList(
            new LambdaQueryWrapper<ModelingModelTagRelPO>()
                .eq(ModelingModelTagRelPO::getProjectId, projectId)
                .in(ModelingModelTagRelPO::getModelId, modelIds))
        .stream()
        .collect(Collectors.groupingBy(
            ModelingModelTagRelPO::getModelId,
            Collectors.mapping(ModelingModelTagRelPO::getTagId, Collectors.toList())));
  }

  @Override
  public List<Long> modelIdsByTagIds(List<Long> tagIds) {
    Long projectId = requiredProjectId();
    if (tagIds == null || tagIds.isEmpty()) {
      return List.of();
    }
    return relMapper.selectList(
            new LambdaQueryWrapper<ModelingModelTagRelPO>()
                .eq(ModelingModelTagRelPO::getProjectId, projectId)
                .in(ModelingModelTagRelPO::getTagId, tagIds))
        .stream()
        .map(ModelingModelTagRelPO::getModelId)
        .distinct()
        .toList();
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static ModelingTag toDomain(ModelingTagPO po) {
    return new ModelingTag(po.getId(), po.getName(), po.getCreateTime());
  }
}
