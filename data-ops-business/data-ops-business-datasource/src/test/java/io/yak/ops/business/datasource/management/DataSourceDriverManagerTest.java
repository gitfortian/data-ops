package io.yak.ops.business.datasource.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DriverJarStore;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 驱动上传入口校验:类型 / 扩展名 / 空内容 / 大小上限,落盘交给端口。 */
class DataSourceDriverManagerTest {

  private final DriverJarStore driverJarStore = mock(DriverJarStore.class);
  private final DataSourceProperties properties = new DataSourceProperties();
  private final DataSourceDriverManager manager =
      new DataSourceDriverManager(driverJarStore, properties);

  @Test
  void normalizesPluginTypeAndReturnsStoredPath() {
    when(driverJarStore.store(any(), any(), any())).thenReturn("MYSQL/mysql.jar");

    assertThat(manager.upload(" mysql ", "mysql.jar", new byte[] {1}))
        .isEqualTo("MYSQL/mysql.jar");

    ArgumentCaptor<String> dbType = ArgumentCaptor.forClass(String.class);
    verify(driverJarStore).store(dbType.capture(), eq("mysql.jar"), any());
    assertThat(dbType.getValue()).isEqualTo("MYSQL");
  }

  @Test
  void rejectsUnknownPluginType() {
    assertThatThrownBy(() -> manager.upload("NOT_A_DB", "a.jar", new byte[] {1}))
        .isInstanceOfSatisfying(
            DataSourceException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(DataSourceErrorCode.INVALID_DB_TYPE));
    verifyNoInteractions(driverJarStore);
  }

  @Test
  void rejectsMissingOrNonJarFileName() {
    assertInvalid(() -> manager.upload("MYSQL", null, new byte[] {1}));
    assertInvalid(() -> manager.upload("MYSQL", "driver.zip", new byte[] {1}));
    verifyNoInteractions(driverJarStore);
  }

  @Test
  void rejectsEmptyContent() {
    assertInvalid(() -> manager.upload("MYSQL", "a.jar", new byte[0]));
    assertInvalid(() -> manager.upload("MYSQL", "a.jar", null));
    verifyNoInteractions(driverJarStore);
  }

  @Test
  void rejectsContentOverSizeLimitWithLimitInMessage() {
    properties.getDriver().setMaxFileSizeBytes(4L);

    assertThatThrownBy(() -> manager.upload("MYSQL", "a.jar", new byte[5]))
        .isInstanceOfSatisfying(
            DataSourceException.class,
            exception -> {
              assertThat(exception.getErrorCode())
                  .isEqualTo(DataSourceErrorCode.DRIVER_UPLOAD_INVALID);
              assertThat(exception.getUserMessage()).contains("4");
            });
    verifyNoInteractions(driverJarStore);
  }

  private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
    assertThatThrownBy(action)
        .isInstanceOfSatisfying(
            DataSourceException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(DataSourceErrorCode.DRIVER_UPLOAD_INVALID));
  }
}
