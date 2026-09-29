package io.yak.ops.business.resource.storage;

import io.yak.ops.common.enums.resource.ResourceStorageType;
import java.io.InputStream;

/** Resource-owned port for physical object operations. */
public interface ResourceStorageGateway {

  ResourceStorageType defaultType();

  void createDirectory(ResourceStorageType type, String storagePath);

  void write(
      ResourceStorageType type,
      String storagePath,
      InputStream inputStream,
      long size,
      String contentType,
      boolean overwrite);

  InputStream open(ResourceStorageType type, String storagePath);

  void move(
      ResourceStorageType type,
      String sourcePath,
      String targetPath,
      boolean overwrite);

  void delete(ResourceStorageType type, String storagePath, boolean recursive);
}
