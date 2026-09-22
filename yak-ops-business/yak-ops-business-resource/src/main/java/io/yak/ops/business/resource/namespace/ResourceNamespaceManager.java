package io.yak.ops.business.resource.namespace;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceNodeFactory;
import io.yak.ops.business.resource.domain.ResourcePath;
import io.yak.ops.business.resource.domain.ResourceStoragePath;
import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.business.resource.repository.ResourceRepository;
import io.yak.ops.business.resource.storage.ResourceStorageGateway;
import io.yak.ops.business.resource.storage.ResourceStorageLifecycle;
import io.yak.ops.business.resource.sync.ResourceChangeDispatcher;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import io.yak.ops.spi.resource.ResourceFileSyncAction;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Owns Resource namespace mutations: directory lifecycle, rename, move and metadata delete. */
@Component
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
public class ResourceNamespaceManager {

  private final ResourceRepository repository;
  private final ResourceNamespaceReader reader;
  private final ResourceParentResolver parents;
  private final ResourceNamePolicy names;
  private final ResourceStorageGateway storage;
  private final ResourceStorageLifecycle storageLifecycle;
  private final ResourceChangeDispatcher changes;

  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceNode createDirectory(ResourceNamespaceCommand.CreateDirectory command) {
    if (command == null) {
      throw new ResourceException(ResourceErrorCode.INVALID_NAME, "创建目录参数不能为空");
    }
    ResourceParentResolver.Parent parent = parents.resolve(command.parentId());
    String name = names.normalize(command.name());
    ensureNameAvailable(parent.id(), name, null);
    ResourcePath fullPath = new ResourcePath(parent.fullPath()).child(name);
    String storagePath = ResourceStoragePath.forProject(parent.projectId(), fullPath);

    storage.createDirectory(parent.storageType(), storagePath);
    try {
      ResourceNode resource =
          ResourceNodeFactory.create(
              parent.id(),
              name,
              fullPath.value(),
              ResourceNodeType.DIRECTORY,
              parent.storageType(),
              storagePath,
              null,
              null,
              0L,
              null,
              command.description());
      resource.setProjectId(parent.projectId());
      insert(resource);
      changes.dispatchAfterCommit(resource, ResourceFileSyncAction.CREATED, null);
      return resource;
    } catch (RuntimeException exception) {
      storageLifecycle.cleanupCreated(parent.storageType(), storagePath, true);
      throw exception;
    }
  }

  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceNode update(Long id, ResourceNamespaceCommand.Update command) {
    if (command == null) {
      throw new ResourceException(ResourceErrorCode.INVALID_NAME, "更新资源参数不能为空");
    }
    ResourceNode resource = reader.require(id);
    String name = names.normalize(command.name());
    ensureNameAvailable(resource.getParentId(), name, resource.getId());
    String oldFullPath = resource.getFullPath();
    if (!name.equals(resource.getName())) {
      relocate(resource, parents.resolve(resource.getParentId()), name);
    }
    resource.setDescription(names.trimToNull(command.description()));
    resource.setUpdateTime(LocalDateTime.now());
    if (!repository.update(resource)) {
      throw new ResourceException(ResourceErrorCode.UPDATE_FAILED);
    }
    changes.dispatchAfterCommit(resource, ResourceFileSyncAction.UPDATED, oldFullPath);
    return resource;
  }

  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceNode move(Long id, ResourceNamespaceCommand.Move command) {
    if (command == null || command.targetParentId() == null) {
      throw new ResourceException(ResourceErrorCode.INVALID_MOVE_TARGET, "目标目录不能为空");
    }
    ResourceNode resource = reader.require(id);
    ResourceParentResolver.Parent targetParent = parents.resolve(command.targetParentId());
    ensureNameAvailable(targetParent.id(), resource.getName(), resource.getId());
    String oldFullPath = resource.getFullPath();
    relocate(resource, targetParent, resource.getName());
    changes.dispatchAfterCommit(resource, ResourceFileSyncAction.MOVED, oldFullPath);
    return resource;
  }

  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public boolean delete(Long id) {
    ResourceNode resource = reader.require(id);
    List<ResourceNode> descendants = repository.findDescendants(resource.getFullPath());
    List<Long> ids = new ArrayList<>(descendants.size() + 1);
    ids.add(resource.getId());
    for (ResourceNode descendant : descendants) ids.add(descendant.getId());
    if (!repository.deleteBatch(ids)) {
      throw new ResourceException(ResourceErrorCode.DELETE_FAILED);
    }

    storageLifecycle.deleteAfterCommit(
        resource.getStorageType(),
        resource.getStoragePath(),
        resource.getNodeType() == ResourceNodeType.DIRECTORY);
    changes.dispatchAfterCommit(
        resource,
        ResourceFileSyncAction.DELETED,
        resource.getFullPath());
    return true;
  }

  private void relocate(
      ResourceNode resource,
      ResourceParentResolver.Parent targetParent,
      String targetName) {
    if (resource.getId().equals(targetParent.id())) {
      throw new ResourceException(ResourceErrorCode.INVALID_MOVE_TARGET);
    }
    if (targetParent.storageType() != resource.getStorageType()) {
      throw new ResourceException(ResourceErrorCode.CROSS_STORAGE_MOVE_UNSUPPORTED);
    }
    if (!Objects.equals(resource.getProjectId(), targetParent.projectId())) {
      throw new ResourceException(ResourceErrorCode.INVALID_MOVE_TARGET);
    }

    ResourcePath oldPath = new ResourcePath(resource.getFullPath());
    ResourcePath newPath = new ResourcePath(targetParent.fullPath()).child(targetName);
    if (newPath.value().equals(oldPath.value())) {
      resource.setName(targetName);
      resource.setParentId(targetParent.id());
      return;
    }
    if (newPath.isDescendantOf(oldPath)) {
      throw new ResourceException(ResourceErrorCode.INVALID_MOVE_TARGET);
    }

    String oldStoragePath = resource.getStoragePath();
    String newStoragePath = ResourceStoragePath.forProject(resource.getProjectId(), newPath);
    storage.move(resource.getStorageType(), oldStoragePath, newStoragePath, false);

    List<ResourceNode> updates = new ArrayList<>();
    resource.setParentId(targetParent.id());
    resource.setName(targetName);
    resource.setFullPath(newPath.value());
    resource.setStoragePath(newStoragePath);
    bumpNamespaceRevision(resource);
    updates.add(resource);

    if (resource.getNodeType() == ResourceNodeType.DIRECTORY) {
      for (ResourceNode descendant : repository.findDescendants(oldPath.value())) {
        String suffix = descendant.getFullPath().substring(oldPath.value().length());
        descendant.setFullPath(newPath.value() + suffix);
        descendant.setStoragePath(
            ResourceStoragePath.forProject(
                descendant.getProjectId(), new ResourcePath(descendant.getFullPath())));
        bumpNamespaceRevision(descendant);
        updates.add(descendant);
      }
    }

    if (!repository.updateBatch(updates)) {
      storageLifecycle.rollbackMove(resource.getStorageType(), newStoragePath, oldStoragePath);
      throw new ResourceException(ResourceErrorCode.UPDATE_FAILED);
    }
  }

  private void ensureNameAvailable(Long parentId, String name, Long excludeId) {
    if (repository.existsByParentAndName(parents.normalize(parentId), name, excludeId)) {
      throw new ResourceException(ResourceErrorCode.DUPLICATE_NAME, name);
    }
  }

  private void insert(ResourceNode resource) {
    if (!repository.insert(resource)) {
      throw new ResourceException(ResourceErrorCode.CREATE_FAILED);
    }
  }

  private void bumpNamespaceRevision(ResourceNode resource) {
    resource.setVersion(resource.getVersion() == null ? 1 : resource.getVersion() + 1);
    resource.setUpdateTime(LocalDateTime.now());
  }
}
