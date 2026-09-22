package io.yak.ops.plugin.database.jdbc;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Class.forName 失败后回退到 DriverManager 按 URL 匹配,覆盖外置上传驱动的可见性。 */
class JdbcDriverLoaderTest {

  @Test
  void loadsClasspathDriverByClassName() {
    assertThatCode(
            () -> JdbcDriverLoader.loadDriver("java.sql.Driver", "jdbc:anything:does-not-matter"))
        .doesNotThrowAnyException();
  }

  @Test
  void fallsBackToDriverManagerWhenClassInvisibleToAppLoader() {
    // mysql 驱动随应用类路径自动注册,按 URL 即可命中,无需类名可见。
    assertThatCode(
            () ->
                JdbcDriverLoader.loadDriver(
                    "com.example.uploaded.OnlyInJarDriver",
                    "jdbc:mysql://127.0.0.1:3306/whatever"))
        .doesNotThrowAnyException();
  }

  @Test
  void rethrowsClassNotFoundExceptionWhenUrlMatchesNoDriver() {
    assertThatThrownBy(
            () ->
                JdbcDriverLoader.loadDriver(
                    "com.example.uploaded.OnlyInJarDriver", "jdbc:no-such-scheme:x"))
        .isInstanceOf(ClassNotFoundException.class);
  }
}
