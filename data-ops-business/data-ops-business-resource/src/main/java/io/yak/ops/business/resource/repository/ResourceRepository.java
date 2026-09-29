package io.yak.ops.business.resource.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceQuery;
import java.util.List;
import java.util.Optional;

/** 资源元数据领域仓储。 */
public interface ResourceRepository {

  Optional<ResourceNode> findById(Long id);

  Optional<ResourceNode> findByFullPath(String fullPath);

  boolean existsByParentAndName(Long parentId, String name, Long excludeId);

  boolean insert(ResourceNode resource);

  boolean update(ResourceNode resource);

  boolean updateBatch(List<ResourceNode> resources);

  boolean deleteBatch(List<Long> ids);

  List<ResourceNode> findChildren(Long parentId, String keyword);

  List<ResourceNode> findAll();

  List<ResourceNode> findDescendants(String fullPath);

  PageData<ResourceNode> page(ResourceQuery query);
}
