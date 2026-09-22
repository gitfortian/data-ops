package io.yak.ops.business.resource.storage;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import io.yak.ops.common.enums.resource.ResourceStorageType;
import io.yak.ops.spi.storage.StoragePluginException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Adapter that keeps StorageOperator SPI details inside the storage subsystem. */
@Component
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
public class StorageOperatorGatewayAdapter implements ResourceStorageGateway {

  private final ResourceStorageRegistry registry;

  @Override
  public ResourceStorageType defaultType() {
    ResourceStorageType type = registry.defaultType();
    registry.require(type);
    return type;
  }

  @Override
  public void createDirectory(ResourceStorageType type, String storagePath) {
    run(() -> registry.require(type).createDirectory(storagePath));
  }

  @Override
  public void write(
      ResourceStorageType type,
      String storagePath,
      InputStream inputStream,
      long size,
      String contentType,
      boolean overwrite) {
    run(() -> registry.require(type).upload(storagePath, inputStream, size, contentType, overwrite));
  }

  @Override
  public InputStream open(ResourceStorageType type, String storagePath) {
    return get(() -> registry.require(type).download(storagePath));
  }

  @Override
  public void move(
      ResourceStorageType type,
      String sourcePath,
      String targetPath,
      boolean overwrite) {
    run(() -> registry.require(type).move(sourcePath, targetPath, overwrite));
  }

  @Override
  public void delete(ResourceStorageType type, String storagePath, boolean recursive) {
    run(() -> registry.require(type).delete(storagePath, recursive));
  }

  private void run(Runnable action) {
    try {
      action.run();
    } catch (ResourceException exception) {
      throw exception;
    } catch (StoragePluginException exception) {
      throw storageException(exception);
    } catch (RuntimeException exception) {
      throw storageException(exception);
    }
  }

  private <T> T get(StorageSupplier<T> supplier) {
    try {
      return supplier.get();
    } catch (ResourceException exception) {
      throw exception;
    } catch (StoragePluginException exception) {
      throw storageException(exception);
    } catch (RuntimeException exception) {
      throw storageException(exception);
    }
  }

  private ResourceException storageException(RuntimeException exception) {
    return new ResourceException(
        ResourceErrorCode.STORAGE_OPERATION_FAILED,
        exception.getMessage(),
        exception);
  }

  @FunctionalInterface
  private interface StorageSupplier<T> {
    T get();
  }
}
