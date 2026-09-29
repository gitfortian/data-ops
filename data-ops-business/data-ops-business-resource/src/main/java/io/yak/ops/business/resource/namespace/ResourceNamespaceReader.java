package io.yak.ops.business.resource.namespace;

import io.yak.framework.common.PageData;
import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceQuery;
import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.business.resource.repository.ResourceRepository;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Query boundary for resource metadata and namespace lookups. */
@Component
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
public class ResourceNamespaceReader {

  private final ResourceRepository repository;
  private final ResourceParentResolver parents;
  private final ResourceNamePolicy names;

  public ResourceNode get(Long id) {
    return require(id);
  }

  public ResourceNode require(Long id) {
    if (id == null || id <= 0L) {
      throw new ResourceException(ResourceErrorCode.NOT_FOUND);
    }
    return repository.findById(id)
        .orElseThrow(() -> new ResourceException(ResourceErrorCode.NOT_FOUND, String.valueOf(id)));
  }

  public ResourceNode requireFile(Long id) {
    ResourceNode resource = require(id);
    if (resource.getNodeType() != ResourceNodeType.FILE) {
      throw new ResourceException(ResourceErrorCode.DIRECTORY_CONTENT_UNSUPPORTED);
    }
    return resource;
  }

  public List<ResourceNode> list(Long parentId, String keyword) {
    return repository.findChildren(
        parents.normalize(parentId),
        names.trimToNull(keyword));
  }

  public PageData<ResourceNode> page(ResourceQuery query) {
    return repository.page(query);
  }
}
