package io.yak.ops.business.resource.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.dao.ResourceDao;
import io.yak.ops.business.resource.dao.mapper.ResourceMapper;
import io.yak.ops.common.bean.po.resource.ResourcePO;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 基于 MyBatis-Plus 的资源数据访问实现。 */
@Repository
@ConditionalOnResourceEnabled
public class ResourceDaoImpl implements ResourceDao {

  private final ResourceMapper resourceMapper;

  public ResourceDaoImpl(ResourceMapper resourceMapper) {
    this.resourceMapper = resourceMapper;
  }

  @Override
  public int insert(ResourcePO resourcePO) {
    return resourceMapper.insert(resourcePO);
  }

  @Override
  public boolean update(ResourcePO resourcePO) {
    return resourcePO != null && resourceMapper.updateById(resourcePO) > 0;
  }

  @Override
  public boolean update(Long projectId, ResourcePO resourcePO) {
    if (projectId == null || resourcePO == null || resourcePO.getId() == null) return false;
    resourcePO.setProjectId(projectId);
    return resourceMapper.update(
            resourcePO,
            Wrappers.<ResourcePO>lambdaUpdate()
                .eq(ResourcePO::getProjectId, projectId)
                .eq(ResourcePO::getId, resourcePO.getId()))
        > 0;
  }

  @Override
  public ResourcePO selectById(Long id) {
    return selectById(null, id);
  }

  @Override
  public ResourcePO selectById(Long projectId, Long id) {
    if (id == null) return null;
    if (projectId == null) return resourceMapper.selectById(id);
    return resourceMapper.selectOne(
        Wrappers.<ResourcePO>lambdaQuery()
            .eq(ResourcePO::getProjectId, projectId)
            .eq(ResourcePO::getId, id));
  }

  @Override
  public ResourcePO selectByFullPath(String fullPath) {
    return selectByFullPath(null, fullPath);
  }

  @Override
  public ResourcePO selectByFullPath(Long projectId, String fullPath) {
    if (!StringUtils.hasText(fullPath)) return null;
    LambdaQueryWrapper<ResourcePO> query =
        Wrappers.<ResourcePO>lambdaQuery()
            .eq(projectId != null, ResourcePO::getProjectId, projectId)
            .eq(ResourcePO::getFullPath, fullPath)
            .orderByAsc(ResourcePO::getId);
    if (projectId != null) return resourceMapper.selectOne(query);
    return resourceMapper.selectList(query).stream().findFirst().orElse(null);
  }

  @Override
  public boolean existsByParentAndName(Long parentId, String name, Long excludeId) {
    return existsByParentAndName(null, parentId, name, excludeId);
  }

  @Override
  public boolean existsByParentAndName(
      Long projectId, Long parentId, String name, Long excludeId) {
    Long count =
        resourceMapper.selectCount(
            Wrappers.<ResourcePO>lambdaQuery()
                .eq(projectId != null, ResourcePO::getProjectId, projectId)
                .eq(ResourcePO::getParentId, parentId == null ? 0L : parentId)
                .eq(ResourcePO::getName, name)
                .ne(excludeId != null, ResourcePO::getId, excludeId));
    return count != null && count > 0L;
  }

  @Override
  public List<ResourcePO> selectChildren(Long parentId, String keyword) {
    return selectChildren(null, parentId, keyword);
  }

  @Override
  public List<ResourcePO> selectChildren(Long projectId, Long parentId, String keyword) {
    return resourceMapper.selectList(
        Wrappers.<ResourcePO>lambdaQuery()
            .eq(projectId != null, ResourcePO::getProjectId, projectId)
            .eq(ResourcePO::getParentId, parentId == null ? 0L : parentId)
            .and(
                StringUtils.hasText(keyword),
                nested ->
                    nested.like(ResourcePO::getName, keyword)
                        .or()
                        .like(ResourcePO::getFullPath, keyword))
            .orderByAsc(ResourcePO::getNodeType)
            .orderByAsc(ResourcePO::getName)
            .orderByAsc(ResourcePO::getId));
  }

  @Override
  public List<ResourcePO> selectAll() {
    return selectAll(null);
  }

  @Override
  public List<ResourcePO> selectAll(Long projectId) {
    return resourceMapper.selectList(
        Wrappers.<ResourcePO>lambdaQuery()
            .eq(projectId != null, ResourcePO::getProjectId, projectId)
            .orderByAsc(ResourcePO::getFullPath)
            .orderByAsc(ResourcePO::getId));
  }

  @Override
  public List<ResourcePO> selectDescendants(String fullPath) {
    return selectDescendants(null, fullPath);
  }

  @Override
  public List<ResourcePO> selectDescendants(Long projectId, String fullPath) {
    if (!StringUtils.hasText(fullPath)) return Collections.emptyList();
    return resourceMapper.selectList(
        Wrappers.<ResourcePO>lambdaQuery()
            .eq(projectId != null, ResourcePO::getProjectId, projectId)
            .likeRight(ResourcePO::getFullPath, fullPath + "/")
            .orderByAsc(ResourcePO::getFullPath));
  }

  @Override
  public IPage<ResourcePO> selectPage(PageQuery query) {
    PageQuery condition = query == null ? new PageQuery(null, 1, 20, null, null, null) : query;
    Page<ResourcePO> page =
        Page.of(Math.max(1, condition.pageNo()), Math.max(1, condition.pageSize()));
    LambdaQueryWrapper<ResourcePO> wrapper = Wrappers.lambdaQuery();
    wrapper
        .eq(condition.projectId() != null, ResourcePO::getProjectId, condition.projectId())
        .eq(condition.parentId() != null, ResourcePO::getParentId, condition.parentId())
        .and(
            StringUtils.hasText(condition.keyword()),
            nested ->
                nested.like(ResourcePO::getName, condition.keyword())
                    .or()
                    .like(ResourcePO::getFullPath, condition.keyword()))
        .eq(condition.nodeType() != null, ResourcePO::getNodeType, condition.nodeType())
        .orderByAsc(ResourcePO::getNodeType)
        .orderByAsc(ResourcePO::getName)
        .orderByAsc(ResourcePO::getId);
    return resourceMapper.selectPage(page, wrapper);
  }

  @Override
  public boolean updateBatch(List<ResourcePO> resources) {
    if (resources == null || resources.isEmpty()) return true;
    for (ResourcePO resource : resources) {
      if (resourceMapper.updateById(resource) <= 0) return false;
    }
    return true;
  }

  @Override
  public boolean updateBatch(Long projectId, List<ResourcePO> resources) {
    if (resources == null || resources.isEmpty()) return true;
    if (projectId == null) return false;
    for (ResourcePO resource : resources) {
      if (!update(projectId, resource)) return false;
    }
    return true;
  }

  @Override
  public boolean deleteBatch(List<Long> ids) {
    return deleteBatch(null, ids);
  }

  @Override
  public boolean deleteBatch(Long projectId, List<Long> ids) {
    if (ids == null || ids.isEmpty()) return false;
    if (projectId == null) return resourceMapper.deleteByIds(ids) == ids.size();
    return resourceMapper.delete(
            Wrappers.<ResourcePO>lambdaQuery()
                .eq(ResourcePO::getProjectId, projectId)
                .in(ResourcePO::getId, ids))
        == ids.size();
  }
}
