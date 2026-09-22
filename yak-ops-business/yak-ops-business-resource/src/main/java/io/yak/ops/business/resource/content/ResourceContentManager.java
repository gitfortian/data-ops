package io.yak.ops.business.resource.content;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.domain.ResourceContent;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceNodeFactory;
import io.yak.ops.business.resource.domain.ResourcePath;
import io.yak.ops.business.resource.domain.ResourceRevision;
import io.yak.ops.business.resource.domain.ResourceStoragePath;
import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.business.resource.namespace.ResourceNamePolicy;
import io.yak.ops.business.resource.namespace.ResourceNamespaceReader;
import io.yak.ops.business.resource.namespace.ResourceParentResolver;
import io.yak.ops.business.resource.repository.ResourceRepository;
import io.yak.ops.business.resource.storage.ResourceStorageGateway;
import io.yak.ops.business.resource.storage.ResourceStorageLifecycle;
import io.yak.ops.business.resource.sync.ResourceChangeDispatcher;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import io.yak.ops.spi.resource.ResourceFileSyncAction;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Owns physical file-content mutations while metadata remains in ResourceRepository. */
@Component
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
public class ResourceContentManager {

  private final ResourceRepository repository;
  private final ResourceNamespaceReader namespace;
  private final ResourceParentResolver parents;
  private final ResourceNamePolicy names;
  private final ResourceContentPolicy policy;
  private final ResourceChecksum checksum;
  private final ResourceStorageGateway storage;
  private final ResourceStorageLifecycle storageLifecycle;
  private final ResourceChangeDispatcher changes;

  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceNode upload(
      Long parentId,
      String requestedName,
      String description,
      ResourceBinarySource source) {
    requireSource(source, "上传文件不能为空");
    policy.ensureFileSize(source.size());
    ResourceParentResolver.Parent parent = parents.resolve(parentId);
    String sourceName = requestedName == null || requestedName.isBlank()
        ? source.fileName()
        : requestedName;
    String name = names.normalize(sourceName);
    ensureNameAvailable(parent.id(), name, null);
    ResourcePath fullPath = new ResourcePath(parent.fullPath()).child(name);
    String storagePath = ResourceStoragePath.forProject(parent.projectId(), fullPath);
    String contentType = policy.contentType(source.contentType());
    String checksumValue = checksum.sha256(source);

    writeSource(parent.storageType(), storagePath, source, contentType, false);
    try {
      ResourceNode resource =
          ResourceNodeFactory.create(
              parent.id(),
              name,
              fullPath.value(),
              ResourceNodeType.FILE,
              parent.storageType(),
              storagePath,
              contentType,
              ResourcePath.suffix(name),
              source.size(),
              checksumValue,
              description);
      resource.setProjectId(parent.projectId());
      insert(resource);
      changes.dispatchAfterCommit(resource, ResourceFileSyncAction.CREATED, null);
      return resource;
    } catch (RuntimeException exception) {
      storageLifecycle.cleanupCreated(parent.storageType(), storagePath, false);
      throw exception;
    }
  }

  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceNode create(ResourceContentCommand.Create command) {
    if (command == null || command.content() == null) {
      throw new ResourceException(ResourceErrorCode.INVALID_NAME, "在线创建内容不能为空");
    }
    byte[] content = command.content().getBytes(StandardCharsets.UTF_8);
    policy.ensureFileSize(content.length);
    ResourceParentResolver.Parent parent = parents.resolve(command.parentId());
    String name = names.normalize(command.name());
    policy.ensureEditableName(name);
    ensureNameAvailable(parent.id(), name, null);
    ResourcePath fullPath = new ResourcePath(parent.fullPath()).child(name);
    String storagePath = ResourceStoragePath.forProject(parent.projectId(), fullPath);
    String contentType = policy.contentType(command.contentType());

    storage.write(
        parent.storageType(),
        storagePath,
        new ByteArrayInputStream(content),
        content.length,
        contentType,
        false);
    try {
      ResourceNode resource =
          ResourceNodeFactory.create(
              parent.id(),
              name,
              fullPath.value(),
              ResourceNodeType.FILE,
              parent.storageType(),
              storagePath,
              contentType,
              ResourcePath.suffix(name),
              (long) content.length,
              checksum.sha256(content),
              command.description());
      resource.setProjectId(parent.projectId());
      insert(resource);
      changes.dispatchAfterCommit(resource, ResourceFileSyncAction.CREATED, null);
      return resource;
    } catch (RuntimeException exception) {
      storageLifecycle.cleanupCreated(parent.storageType(), storagePath, false);
      throw exception;
    }
  }

  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceNode replaceFile(Long id, ResourceBinarySource source) {
    requireSource(source, "更新文件不能为空");
    policy.ensureFileSize(source.size());
    ResourceNode resource = namespace.requireFile(id);
    String contentType = policy.contentType(source.contentType());
    String checksumValue = checksum.sha256(source);
    writeSource(
        resource.getStorageType(),
        resource.getStoragePath(),
        source,
        contentType,
        true);

    ResourceRevision.current(resource)
        .next(source.size(), checksumValue, contentType)
        .applyTo(resource);
    resource.setUpdateTime(LocalDateTime.now());
    if (!repository.update(resource)) {
      throw new ResourceException(ResourceErrorCode.UPDATE_FAILED);
    }
    changes.dispatchAfterCommit(
        resource,
        ResourceFileSyncAction.UPDATED,
        resource.getFullPath());
    return resource;
  }

  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceContent updateContent(Long id, ResourceContentCommand.Update command) {
    if (command == null || command.content() == null) {
      throw new ResourceException(ResourceErrorCode.CONTENT_NOT_EDITABLE, "文件内容不能为空");
    }
    ResourceNode resource = namespace.requireFile(id);
    policy.ensureEditable(resource);
    byte[] content = command.content().getBytes(StandardCharsets.UTF_8);
    policy.ensureFileSize(content.length);
    policy.ensureEditableContentSize(content.length);
    String contentType = policy.contentType(resource.getContentType());

    storage.write(
        resource.getStorageType(),
        resource.getStoragePath(),
        new ByteArrayInputStream(content),
        content.length,
        contentType,
        true);
    ResourceRevision.current(resource)
        .next(content.length, checksum.sha256(content), contentType)
        .applyTo(resource);
    resource.setUpdateTime(LocalDateTime.now());
    if (!repository.update(resource)) {
      throw new ResourceException(ResourceErrorCode.UPDATE_FAILED);
    }
    changes.dispatchAfterCommit(
        resource,
        ResourceFileSyncAction.UPDATED,
        resource.getFullPath());
    return new ResourceContent(
        resource.getId(),
        resource.getFullPath(),
        command.content(),
        0,
        lineCount(command.content()),
        false);
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

  private void requireSource(ResourceBinarySource source, String message) {
    if (source == null || source.size() <= 0L) {
      throw new ResourceException(ResourceErrorCode.INVALID_NAME, message);
    }
  }

  private void writeSource(
      io.yak.ops.common.enums.resource.ResourceStorageType storageType,
      String storagePath,
      ResourceBinarySource source,
      String contentType,
      boolean overwrite) {
    try (InputStream inputStream = source.openStream()) {
      storage.write(
          storageType,
          storagePath,
          inputStream,
          source.size(),
          contentType,
          overwrite);
    } catch (IOException exception) {
      throw new ResourceException(
          ResourceErrorCode.STORAGE_OPERATION_FAILED,
          "读取上传文件失败",
          exception);
    }
  }

  private int lineCount(String content) {
    if (content == null || content.isEmpty()) return 0;
    return (int) content.lines().count();
  }
}
