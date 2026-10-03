package io.yak.ops.business.modeling.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingDirectoryMapper;
import io.yak.ops.business.modeling.domain.ModelingDirectory;
import io.yak.ops.business.modeling.dao.model.ModelingDirectoryPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * MyBatis adapter for modeling directories. Root directories persist
 * parent_id=0 and surface as parentId=null; every query is project-scoped.
 */
@Repository
public class ModelDirectoryRepositoryAdapter implements ModelDirectoryRepository {

  private static final long ROOT_PARENT_ID = 0L;

  private final ModelingDirectoryMapper mapper;
  private final CurrentProject currentProject;

  @Autowired
  public ModelDirectoryRepositoryAdapter(ModelingDirectoryMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused tests; project-scoped operations will fail closed. */
  public ModelDirectoryRepositoryAdapter(ModelingDirectoryMapper mapper) {
    this(mapper, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public ModelingDirectory insert(Long parentId, String name, Long domainId) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    ModelingDirectoryPO po = new ModelingDirectoryPO();
    po.setProjectId(projectId);
    po.setParentId(toStoredParentId(parentId));
    po.setName(name);
    po.setDomainId(domainId);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return toDomain(po);
  }

  @Override
  public Optional<ModelingDirectory> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<ModelingDirectoryPO>()
                    .eq(ModelingDirectoryPO::getId, id)
                    .eq(ModelingDirectoryPO::getProjectId, projectId)))
        .map(ModelDirectoryRepositoryAdapter::toDomain);
  }

  @Override
  public Optional<ModelingDirectory> findByDomainId(Long domainId) {
    if (domainId == null) {
      return Optional.empty();
    }
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<ModelingDirectoryPO>()
                    .eq(ModelingDirectoryPO::getProjectId, projectId)
                    .eq(ModelingDirectoryPO::getDomainId, domainId)
                    .orderByAsc(ModelingDirectoryPO::getId)
                    .last("LIMIT 1")))
        .map(ModelDirectoryRepositoryAdapter::toDomain);
  }

  @Override
  public Optional<ModelingDirectory> findByParentAndName(Long parentId, String name) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<ModelingDirectoryPO>()
                    .eq(ModelingDirectoryPO::getProjectId, projectId)
                    .eq(ModelingDirectoryPO::getParentId, toStoredParentId(parentId))
                    .eq(ModelingDirectoryPO::getName, name)
                    .orderByAsc(ModelingDirectoryPO::getId)
                    .last("LIMIT 1")))
        .map(ModelDirectoryRepositoryAdapter::toDomain);
  }

  @Override
  public boolean existsByName(Long parentId, String name) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<ModelingDirectoryPO>()
                .eq(ModelingDirectoryPO::getProjectId, projectId)
                .eq(ModelingDirectoryPO::getParentId, toStoredParentId(parentId))
                .eq(ModelingDirectoryPO::getName, name))
        > 0L;
  }

  @Override
  public boolean hasChildren(Long id) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<ModelingDirectoryPO>()
                .eq(ModelingDirectoryPO::getProjectId, projectId)
                .eq(ModelingDirectoryPO::getParentId, id))
        > 0L;
  }

  @Override
  public List<ModelingDirectory> listAll() {
    Long projectId = requiredProjectId();
    return mapper.selectList(
            new LambdaQueryWrapper<ModelingDirectoryPO>()
                .eq(ModelingDirectoryPO::getProjectId, projectId)
                .orderByAsc(ModelingDirectoryPO::getName)
                .orderByAsc(ModelingDirectoryPO::getId))
        .stream()
        .map(ModelDirectoryRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean updateName(Long id, String name) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<ModelingDirectoryPO>()
                .eq(ModelingDirectoryPO::getId, id)
                .eq(ModelingDirectoryPO::getProjectId, projectId)
                .set(ModelingDirectoryPO::getName, name)
                .set(ModelingDirectoryPO::getUpdateTime, LocalDateTime.now()))
        > 0;
  }

  @Override
  public boolean bindDomain(Long id, Long domainId) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<ModelingDirectoryPO>()
                .eq(ModelingDirectoryPO::getId, id)
                .eq(ModelingDirectoryPO::getProjectId, projectId)
                .set(ModelingDirectoryPO::getDomainId, domainId)
                .set(ModelingDirectoryPO::getUpdateTime, LocalDateTime.now()))
        > 0;
  }

  @Override
  public boolean updateParentId(Long id, Long parentId) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<ModelingDirectoryPO>()
                .eq(ModelingDirectoryPO::getId, id)
                .eq(ModelingDirectoryPO::getProjectId, projectId)
                .set(ModelingDirectoryPO::getParentId, toStoredParentId(parentId))
                .set(ModelingDirectoryPO::getUpdateTime, LocalDateTime.now()))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<ModelingDirectoryPO>()
                .eq(ModelingDirectoryPO::getId, id)
                .eq(ModelingDirectoryPO::getProjectId, projectId))
        > 0;
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private Long toStoredParentId(Long parentId) {
    return parentId == null || parentId <= 0L ? ROOT_PARENT_ID : parentId;
  }

  private static ModelingDirectory toDomain(ModelingDirectoryPO po) {
    Long parentId = po.getParentId() == null || po.getParentId() == ROOT_PARENT_ID
        ? null
        : po.getParentId();
    return new ModelingDirectory(
        po.getId(), parentId, po.getName(), po.getCreateTime(), po.getUpdateTime(), po.getDomainId());
  }
}
