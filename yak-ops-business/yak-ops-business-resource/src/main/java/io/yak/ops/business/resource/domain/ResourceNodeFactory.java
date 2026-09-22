package io.yak.ops.business.resource.domain;

import io.yak.ops.common.enums.resource.ResourceNodeType;
import io.yak.ops.common.enums.resource.ResourceStorageType;
import java.time.LocalDateTime;

/** Creates ResourceNode metadata with the module's stable defaults. */
public final class ResourceNodeFactory {

  private ResourceNodeFactory() {
  }

  public static ResourceNode create(
      Long parentId,
      String name,
      String fullPath,
      ResourceNodeType nodeType,
      ResourceStorageType storageType,
      String storagePath,
      String contentType,
      String suffix,
      Long fileSize,
      String checksum,
      String description) {
    LocalDateTime now = LocalDateTime.now();
    ResourceNode resource = new ResourceNode();
    resource.setParentId(parentId == null || parentId <= 0L ? 0L : parentId);
    resource.setName(name);
    resource.setFullPath(fullPath);
    resource.setNodeType(nodeType);
    resource.setStorageType(storageType);
    resource.setStoragePath(storagePath);
    resource.setContentType(contentType);
    resource.setSuffix(suffix);
    resource.setFileSize(fileSize == null ? 0L : fileSize);
    resource.setChecksum(checksum);
    resource.setDescription(trimToNull(description));
    resource.setVersion(1);
    resource.setGitSyncStatus("NONE");
    resource.setCreateTime(now);
    resource.setUpdateTime(now);
    return resource;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
