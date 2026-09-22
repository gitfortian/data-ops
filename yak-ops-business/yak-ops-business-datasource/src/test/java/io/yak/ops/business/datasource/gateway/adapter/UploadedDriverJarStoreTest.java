package io.yak.ops.business.datasource.gateway.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.plugin.DataSourceDriverRegistry;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 驱动包落盘:文件名清洗 / 防穿越 / 注册失败回滚。注册表用 mock 隔离 DriverManager。 */
class UploadedDriverJarStoreTest {

  @TempDir Path root;

  private final DataSourceProperties properties = new DataSourceProperties();
  private final DataSourceDriverRegistry registry = mock(DataSourceDriverRegistry.class);
  private final UploadedDriverJarStore store = new UploadedDriverJarStore(properties, registry);

  @BeforeEach
  void pointStorageAtTempRoot() {
    properties.getDriver().setDirectory(root.toString());
  }

  @Test
  void storesSanitizedJarUnderDbTypeDirectoryAndReturnsRelativePath() throws Exception {
    when(registry.register(eq("MYSQL"), any())).thenReturn(1);

    String path = store.store("MYSQL", "C:\\downloads\\mysql driver-8.0.jar", jarBytes());

    assertThat(path).isEqualTo("MYSQL/mysql_driver-8.0.jar");
    assertThat(root.resolve("MYSQL/mysql_driver-8.0.jar")).exists();
    verify(registry).register(eq("MYSQL"), any(Path.class));
  }

  @Test
  void stripsDirectoryPartsFromFileNameToPreventTraversal() throws Exception {
    when(registry.register(eq("MYSQL"), any())).thenReturn(1);

    String path = store.store("MYSQL", "../../evil.jar", jarBytes());

    assertThat(path).isEqualTo("MYSQL/evil.jar");
    assertThat(root.resolve("evil.jar")).doesNotExist();
  }

  @Test
  void rejectsNonJarFileNameBeforeTouchingDisk() {
    assertThatThrownBy(() -> store.store("MYSQL", "readme.txt", jarBytes()))
        .isInstanceOfSatisfying(
            DataSourceException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(DataSourceErrorCode.DRIVER_UPLOAD_INVALID));
    verifyNoInteractions(registry);
  }

  @Test
  void removesStoredFileWhenDriverRegistrationRejectsJar() throws Exception {
    doThrow(new DataSourceException(DataSourceErrorCode.DRIVER_UPLOAD_INVALID))
        .when(registry)
        .register(eq("ORACLE"), any());

    assertThatThrownBy(() -> store.store("ORACLE", "ojdbc.jar", jarBytes()))
        .isInstanceOfSatisfying(
            DataSourceException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(DataSourceErrorCode.DRIVER_UPLOAD_INVALID));
    assertThat(root.resolve("ORACLE/ojdbc.jar")).doesNotExist();
  }

  @Test
  void wrapsIoFailureAsUploadFailedAndRollsBackFile() throws Exception {
    doThrow(new IOException("disk full")).when(registry).register(eq("MYSQL"), any());

    assertThatThrownBy(() -> store.store("MYSQL", "mysql.jar", jarBytes()))
        .isInstanceOfSatisfying(
            DataSourceException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(DataSourceErrorCode.DRIVER_UPLOAD_FAILED));
    assertThat(root.resolve("MYSQL/mysql.jar")).doesNotExist();
  }

  private static byte[] jarBytes() {
    // 落盘与注册解耦:内容合法性由注册表 ServiceLoader 环节把关,这里 mock 掉。
    return new byte[] {1, 2, 3};
  }
}
