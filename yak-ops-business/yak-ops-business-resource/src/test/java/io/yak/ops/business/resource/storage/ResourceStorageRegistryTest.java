package io.yak.ops.business.resource.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.resource.config.ResourceProperties;
import io.yak.ops.business.resource.domain.ResourceStoragePlugin;
import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.common.enums.resource.ResourceStorageType;
import io.yak.ops.spi.storage.StorageObjectMetadata;
import io.yak.ops.spi.storage.StorageOperator;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceStorageRegistryTest {

  @Test
  void resolvesLocalAsConfiguredDefaultAndListsInstalledPlugins() {
    ResourceProperties properties = new ResourceProperties();
    StorageOperator local = new StubOperator(ResourceStorageType.LOCAL, "本地存储");
    StorageOperator minio = new StubOperator(ResourceStorageType.MINIO, "MinIO");
    ResourceStorageRegistry registry = new ResourceStorageRegistry(List.of(local, minio), properties);

    assertThat(registry.require(null)).isSameAs(local);
    List<ResourceStoragePlugin> plugins = registry.list();
    assertThat(plugins).hasSize(2);
    assertThat(plugins.get(0).type()).isEqualTo(ResourceStorageType.LOCAL);
    assertThat(plugins.get(0).active()).isTrue();
    assertThat(plugins.get(1).type()).isEqualTo(ResourceStorageType.MINIO);
    assertThat(plugins.get(1).active()).isFalse();
  }

  @Test
  void rejectsMissingConfiguredPlugin() {
    ResourceProperties properties = new ResourceProperties();
    properties.getStorage().setType(ResourceStorageType.HDFS);
    ResourceStorageRegistry registry = new ResourceStorageRegistry(List.of(), properties);

    assertThatThrownBy(() -> registry.require(null)).isInstanceOf(ResourceException.class);
  }

  private static final class StubOperator implements StorageOperator {
    private final ResourceStorageType type;
    private final String name;

    private StubOperator(ResourceStorageType type, String name) {
      this.type = type;
      this.name = name;
    }

    @Override
    public ResourceStorageType type() {
      return type;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public void createDirectory(String path) {
    }

    @Override
    public boolean exists(String path) {
      return false;
    }

    @Override
    public void upload(
        String path,
        InputStream inputStream,
        long size,
        String contentType,
        boolean overwrite) {
    }

    @Override
    public InputStream download(String path) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete(String path, boolean recursive) {
    }

    @Override
    public void move(String sourcePath, String targetPath, boolean overwrite) {
    }

    @Override
    public StorageObjectMetadata metadata(String path) {
      throw new UnsupportedOperationException();
    }
  }
}
