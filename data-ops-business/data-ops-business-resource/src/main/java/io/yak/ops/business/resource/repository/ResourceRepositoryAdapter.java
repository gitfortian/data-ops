package io.yak.ops.business.resource.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.dao.ResourceDao;
import io.yak.ops.business.resource.dao.ResourceDao.PageQuery;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceQuery;
import io.yak.ops.common.bean.po.resource.ResourcePO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** Explicit persistence adapter between Resource domain metadata and MyBatis persistence models. */
@Repository
@ConditionalOnResourceEnabled
public class ResourceRepositoryAdapter implements ResourceRepository {

  private final ResourceDao resourceDao;
  private final CurrentProject currentProject;

  @Autowired
  public ResourceRepositoryAdapter(ResourceDao resourceDao, CurrentProject currentProject) {
    this.resourceDao = resourceDao;
    this.currentProject = currentProject;
  }

  public ResourceRepositoryAdapter(ResourceDao resourceDao) {
    this(resourceDao, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public Optional<ResourceNode> findById(Long id) {
    Long projectId = currentProjectId();
    // Temporary read-only runtime corridor: ResourceResolver can still be entered by legacy task
    // runtimes that have not restored ProjectContext yet. HTTP Resource APIs are PROJECT_REQUIRED,
    // and every broad query/mutation below is fail-closed. Remove this fallback after task runtime
    // project restoration is complete.
    ResourcePO row =
        projectId == null ? resourceDao.selectById(id) : resourceDao.selectById(projectId, id);
    return Optional.ofNullable(row).map(this::toDomain);
  }

  @Override
  public Optional<ResourceNode> findByFullPath(String fullPath) {
    long projectId = requireProjectId();
    return Optional.ofNullable(resourceDao.selectByFullPath(projectId, fullPath)).map(this::toDomain);
  }

  @Override
  public boolean existsByParentAndName(Long parentId, String name, Long excludeId) {
    return resourceDao.existsByParentAndName(requireProjectId(), parentId, name, excludeId);
  }

  @Override
  public boolean insert(ResourceNode resource) {
    if (resource == null) return false;
    long projectId = requireProjectId();
    ensureCurrentProject(resource.getProjectId(), projectId);
    resource.setProjectId(projectId);
    ResourcePO po = toPersistence(resource);
    boolean inserted = resourceDao.insert(po) > 0;
    if (inserted) resource.setId(po.getId());
    return inserted;
  }

  @Override
  public boolean update(ResourceNode resource) {
    if (resource == null) return false;
    long projectId = requireProjectId();
    ensureCurrentProject(resource.getProjectId(), projectId);
    resource.setProjectId(projectId);
    return resourceDao.update(projectId, toPersistence(resource));
  }

  @Override
  public boolean updateBatch(List<ResourceNode> resources) {
    if (resources == null || resources.isEmpty()) return true;
    long projectId = requireProjectId();
    resources.forEach(
        resource -> {
          ensureCurrentProject(resource.getProjectId(), projectId);
          resource.setProjectId(projectId);
        });
    return resourceDao.updateBatch(
        projectId, resources.stream().map(this::toPersistence).toList());
  }

  @Override
  public boolean deleteBatch(List<Long> ids) {
    return resourceDao.deleteBatch(requireProjectId(), ids);
  }

  @Override
  public List<ResourceNode> findChildren(Long parentId, String keyword) {
    return resourceDao.selectChildren(requireProjectId(), parentId, keyword).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<ResourceNode> findAll() {
    return resourceDao.selectAll(requireProjectId()).stream().map(this::toDomain).toList();
  }

  @Override
  public List<ResourceNode> findDescendants(String fullPath) {
    return resourceDao.selectDescendants(requireProjectId(), fullPath).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public PageData<ResourceNode> page(ResourceQuery query) {
    ResourceQuery condition =
        query == null ? new ResourceQuery(1, 20, null, null, null) : query;
    IPage<ResourcePO> page =
        resourceDao.selectPage(
            new PageQuery(
                requireProjectId(),
                condition.pageNo(),
                condition.pageSize(),
                condition.parentId(),
                condition.keyword(),
                condition.nodeType()));
    return new PageData<>(
        page.getRecords().stream().map(this::toDomain).toList(),
        page.getTotal(),
        page.getPages(),
        page.getCurrent(),
        page.getSize());
  }

  private Long currentProjectId() {
    return currentProject.current().map(context -> context.projectId()).orElse(null);
  }

  private long requireProjectId() {
    return currentProject.requireProjectId();
  }

  private void ensureCurrentProject(Long ownerProjectId, long currentProjectId) {
    if (ownerProjectId != null && !Objects.equals(currentProjectId, ownerProjectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
  }

  ResourceNode toDomain(ResourcePO po) {
    if (po == null) return null;
    ResourceNode domain = new ResourceNode();
    domain.setId(po.getId());
    domain.setProjectId(po.getProjectId());
    domain.setParentId(po.getParentId());
    domain.setName(po.getName());
    domain.setFullPath(po.getFullPath());
    domain.setNodeType(po.getNodeType());
    domain.setStorageType(po.getStorageType());
    domain.setStoragePath(po.getStoragePath());
    domain.setContentType(po.getContentType());
    domain.setSuffix(po.getSuffix());
    domain.setFileSize(po.getFileSize());
    domain.setChecksum(po.getChecksum());
    domain.setDescription(po.getDescription());
    domain.setVersion(po.getVersion());
    domain.setGitSyncStatus(po.getGitSyncStatus());
    domain.setCreateTime(po.getCreateTime());
    domain.setUpdateTime(po.getUpdateTime());
    return domain;
  }

  ResourcePO toPersistence(ResourceNode domain) {
    ResourcePO po = new ResourcePO();
    po.setId(domain.getId());
    po.setProjectId(domain.getProjectId());
    po.setParentId(domain.getParentId());
    po.setName(domain.getName());
    po.setFullPath(domain.getFullPath());
    po.setNodeType(domain.getNodeType());
    po.setStorageType(domain.getStorageType());
    po.setStoragePath(domain.getStoragePath());
    po.setContentType(domain.getContentType());
    po.setSuffix(domain.getSuffix());
    po.setFileSize(domain.getFileSize());
    po.setChecksum(domain.getChecksum());
    po.setDescription(domain.getDescription());
    po.setVersion(domain.getVersion());
    po.setGitSyncStatus(domain.getGitSyncStatus());
    po.setCreateTime(domain.getCreateTime());
    po.setUpdateTime(domain.getUpdateTime());
    return po;
  }
}
