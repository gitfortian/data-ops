package io.yak.ops.business.datasource.gateway.adapter;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DriverJarStore;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.plugin.DataSourceDriverRegistry;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 驱动包本地落盘 + 注册实现:存储根目录可配,写入前防路径穿越,注册失败回滚文件。 */
@Component
@ConditionalOnDataSourceEnabled
public class UploadedDriverJarStore implements DriverJarStore {

  private static final Pattern UNSAFE_NAME = Pattern.compile("[^A-Za-z0-9._-]");

  private final DataSourceProperties properties;
  private final DataSourceDriverRegistry registry;

  public UploadedDriverJarStore(DataSourceProperties properties, DataSourceDriverRegistry registry) {
    this.properties = properties;
    this.registry = registry;
  }

  @Override
  public String store(String dbType, String fileName, byte[] content) {
    Path root = Path.of(properties.getDriver().getDirectory()).toAbsolutePath().normalize();
    String safeName = sanitize(baseName(fileName));
    if (!safeName.toLowerCase().endsWith(".jar") || safeName.isBlank() || safeName.equals(".jar")) {
      throw new DataSourceException(DataSourceErrorCode.DRIVER_UPLOAD_INVALID);
    }
    Path directory = root.resolve(sanitize(dbType));
    Path target = directory.resolve(safeName).normalize();
    if (!target.startsWith(root)) {
      throw new DataSourceException(DataSourceErrorCode.DRIVER_UPLOAD_INVALID);
    }
    try {
      Files.createDirectories(directory);
      Path staging = Files.createTempFile(directory, safeName, ".staging");
      Files.write(staging, content);
      Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException failure) {
      throw new DataSourceException(
          DataSourceErrorCode.DRIVER_UPLOAD_FAILED, failure.getMessage(), failure);
    }
    try {
      registry.register(dbType, target);
    } catch (DataSourceException invalid) {
      deleteQuietly(target);
      throw invalid;
    } catch (IOException | java.sql.SQLException | RuntimeException failure) {
      deleteQuietly(target);
      throw new DataSourceException(
          DataSourceErrorCode.DRIVER_UPLOAD_FAILED, failure.getMessage(), failure);
    }
    return root.relativize(target).toString().replace('\\', '/');
  }

  private static String baseName(String fileName) {
    if (fileName == null) return "";
    String normalized = fileName.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    return slash < 0 ? normalized : normalized.substring(slash + 1);
  }

  private static String sanitize(String value) {
    return UNSAFE_NAME.matcher(value == null ? "" : value).replaceAll("_");
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // 清理失败不改变业务结论。
    }
  }
}
