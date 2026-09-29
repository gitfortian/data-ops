package io.yak.ops.business.development.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.yak.ops.business.development.dao.mapper.DevelopmentDirectoryMapper;
import io.yak.ops.business.development.domain.DevelopmentDirectory;
import io.yak.ops.common.bean.po.development.DevelopmentDirectoryPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for hierarchical data-development directories. */
@Repository
public class DevelopmentDirectoryRepositoryAdapter implements DevelopmentDirectoryRepository {

  private static final long ROOT_PARENT_ID = 0L;

  private final DevelopmentDirectoryMapper mapper;
  private final CurrentProject currentProject;

  @Autowired
  public DevelopmentDirectoryRepositoryAdapter(
      DevelopmentDirectoryMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused tests; project-scoped operations will fail closed. */
  public DevelopmentDirectoryRepositoryAdapter(DevelopmentDirectoryMapper mapper) {
    this(mapper, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public DevelopmentDirectory insert(Long parentId, String name) {
    Long projectId = requiredProjectId();
    Instant now = Instant.now();
    DevelopmentDirectoryPO po = new DevelopmentDirectoryPO();
    po.setProjectId(projectId);
    po.setParentId(toStoredParentId(parentId));
    po.setName(name);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return toDomain(po);
  }

  @Override
  public Optional<DevelopmentDirectory> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<DevelopmentDirectoryPO>()
                    .eq(DevelopmentDirectoryPO::getId, id)
                    .eq(DevelopmentDirectoryPO::getProjectId, projectId)))
        .map(this::toDomain);
  }

  @Override
  public List<DevelopmentDirectory> list() {
    Long projectId = requiredProjectId();
    return mapper.selectList(
            new LambdaQueryWrapper<DevelopmentDirectoryPO>()
                .eq(DevelopmentDirectoryPO::getProjectId, projectId)
                .orderByAsc(DevelopmentDirectoryPO::getName)
                .orderByAsc(DevelopmentDirectoryPO::getId))
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public boolean existsByName(Long parentId, String name) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<DevelopmentDirectoryPO>()
                .eq(DevelopmentDirectoryPO::getProjectId, projectId)
                .eq(DevelopmentDirectoryPO::getParentId, toStoredParentId(parentId))
                .eq(DevelopmentDirectoryPO::getName, name))
        > 0L;
  }

  @Override
  public boolean hasChildren(Long id) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<DevelopmentDirectoryPO>()
                .eq(DevelopmentDirectoryPO::getProjectId, projectId)
                .eq(DevelopmentDirectoryPO::getParentId, id))
        > 0L;
  }

  @Override
  public boolean updateName(Long id, String name) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<DevelopmentDirectoryPO>()
                .eq(DevelopmentDirectoryPO::getId, id)
                .eq(DevelopmentDirectoryPO::getProjectId, projectId)
                .set(DevelopmentDirectoryPO::getName, name)
                .set(DevelopmentDirectoryPO::getUpdateTime, Instant.now()))
        > 0;
  }

  @Override
  public boolean updateParentId(Long id, Long parentId) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<DevelopmentDirectoryPO>()
                .eq(DevelopmentDirectoryPO::getId, id)
                .eq(DevelopmentDirectoryPO::getProjectId, projectId)
                .set(DevelopmentDirectoryPO::getParentId, toStoredParentId(parentId))
                .set(DevelopmentDirectoryPO::getUpdateTime, Instant.now()))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<DevelopmentDirectoryPO>()
                .eq(DevelopmentDirectoryPO::getId, id)
                .eq(DevelopmentDirectoryPO::getProjectId, projectId))
        > 0;
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private Long toStoredParentId(Long parentId) {
    return parentId == null || parentId <= 0L ? ROOT_PARENT_ID : parentId;
  }

  private DevelopmentDirectory toDomain(DevelopmentDirectoryPO po) {
    Long parentId = po.getParentId() == null || po.getParentId() == ROOT_PARENT_ID
        ? null
        : po.getParentId();
    return new DevelopmentDirectory(
        po.getId(),
        parentId,
        po.getName(),
        null,
        po.getCreateTime(),
        po.getUpdateTime());
  }
}
