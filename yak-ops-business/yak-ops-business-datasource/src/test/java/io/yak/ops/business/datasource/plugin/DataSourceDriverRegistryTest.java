package io.yak.ops.business.datasource.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 外置驱动注册表:用运行时编译现造一个「仅存在于上传 jar 内」的 Driver 验证真实加载路径。 */
class DataSourceDriverRegistryTest {

  private static final String FIXTURE_URL = "jdbc:yakfixture:probe";

  private static final String FIXTURE_DRIVER_SOURCE =
      """
      package yak.fixture;

      public class FixtureDriver implements java.sql.Driver {
        @Override
        public java.sql.Connection connect(String url, java.util.Properties info) {
          return null;
        }

        @Override
        public boolean acceptsURL(String url) {
          return url != null && url.startsWith("jdbc:yakfixture:");
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(
            String url, java.util.Properties info) {
          return new java.sql.DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
          return 1;
        }

        @Override
        public int getMinorVersion() {
          return 0;
        }

        @Override
        public boolean jdbcCompliant() {
          return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger()
            throws java.sql.SQLFeatureNotSupportedException {
          throw new java.sql.SQLFeatureNotSupportedException();
        }
      }
      """;

  private final DataSourceDriverRegistry registry = new DataSourceDriverRegistry();

  @AfterEach
  void releaseFixtureRegistration() {
    registry.unregister("FIXTURE");
  }

  @Test
  void registersUploadedDriverThroughShimAndUnregisterRemovesIt(@TempDir Path dir)
      throws Exception {
    Path jar = driverJar(dir, true);

    assertThat(registry.register("FIXTURE", jar)).isEqualTo(1);
    assertThat(DriverManager.getDriver(FIXTURE_URL)).isNotNull();

    registry.unregister("FIXTURE");
    assertThatThrownBy(() -> DriverManager.getDriver(FIXTURE_URL)).isInstanceOf(SQLException.class);
  }

  @Test
  void repeatedUploadForSameDbTypeReplacesPreviousRegistration(@TempDir Path dir)
      throws Exception {
    Path jar = driverJar(dir, true);

    registry.register("FIXTURE", jar);
    assertThat(registry.register("FIXTURE", jar)).isEqualTo(1);
    assertThat(fixtureDriverCount()).isEqualTo(1);
  }

  @Test
  void jarWithoutDeclaredJdbcDriverIsRejected(@TempDir Path dir) throws Exception {
    Path jar = driverJar(dir, false);

    assertThatThrownBy(() -> registry.register("FIXTURE", jar))
        .isInstanceOfSatisfying(
            DataSourceException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(DataSourceErrorCode.DRIVER_UPLOAD_INVALID));
    assertThat(fixtureDriverCount()).isZero();
  }

  private Path driverJar(Path dir, boolean withServiceEntry) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    Assumptions.assumeTrue(compiler != null, "测试需要 JDK 编译器");
    Path sourceFile = dir.resolve("src/yak/fixture/FixtureDriver.java");
    Files.createDirectories(sourceFile.getParent());
    Files.writeString(sourceFile, FIXTURE_DRIVER_SOURCE);
    Path classes = dir.resolve("classes");
    assertThat(compiler.run(null, null, null, "-d", classes.toString(), sourceFile.toString()))
        .isZero();

    Path jar = dir.resolve("fixture-driver.jar");
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
      if (withServiceEntry) {
        out.putNextEntry(new JarEntry("META-INF/services/java.sql.Driver"));
        out.write("yak.fixture.FixtureDriver\n".getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
      byte[] driverClass =
          Files.readAllBytes(classes.resolve("yak/fixture/FixtureDriver.class"));
      out.putNextEntry(new JarEntry("yak/fixture/FixtureDriver.class"));
      out.write(driverClass);
      out.closeEntry();
    }
    return jar;
  }

  private static long fixtureDriverCount() {
    return Collections.list(DriverManager.getDrivers())
        .stream()
        .filter(
            driver -> {
              try {
                return driver.acceptsURL(FIXTURE_URL);
              } catch (SQLException failure) {
                return false;
              }
            })
        .count();
  }
}
