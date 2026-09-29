package io.yak.ops.business.datasource.plugin;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 外置 JDBC 驱动注册表(Ticket 02):把上传 jar 里的 java.sql.Driver 经 DriverShim 注册进 DriverManager。
 * 隔离姿势=每 jar 一个 URLClassLoader,持有强引用防 GC;同 dbType 重复上传时先注销旧的。
 * 不做热卸载之外的生命周期管理:ClassLoader 一旦有连接存活无法真正释放,仅关闭引用。
 */
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceDriverRegistry {

  private record Loaded(URLClassLoader classLoader, List<Driver> shims) {}

  private final Map<String, Loaded> loadedByType = new ConcurrentHashMap<>();

  /** 注册 jar 内的全部 JDBC 驱动;jar 内没有 Driver 实现则报 41015。 */
  public synchronized int register(String dbType, Path jarFile)
      throws IOException, SQLException {
    unregister(dbType);
    URLClassLoader classLoader =
        new URLClassLoader(
            "yak-driver-" + dbType,
            new URL[] {jarFile.toAbsolutePath().toUri().toURL()},
            getClass().getClassLoader());
    List<Driver> shims = new ArrayList<>();
    try {
      for (Driver driver : ServiceLoader.load(Driver.class, classLoader)) {
        // ServiceLoader 会聚合父类路径上的驱动声明;只注册本 jar 独有类,应用内置驱动无需重复注册。
        if (driver.getClass().getClassLoader() != classLoader) {
          continue;
        }
        DriverShim shim = new DriverShim(driver);
        DriverManager.registerDriver(shim);
        shims.add(shim);
      }
      // 厂商驱动类的 static 块会自注册(子加载器版本对调用方不可见),一并注销防泄漏。
      for (Driver registered : Collections.list(DriverManager.getDrivers())) {
        if (registered.getClass().getClassLoader() == classLoader) {
          DriverManager.deregisterDriver(registered);
        }
      }
      if (shims.isEmpty()) {
        throw new DataSourceException(DataSourceErrorCode.DRIVER_UPLOAD_INVALID);
      }
      loadedByType.put(dbType, new Loaded(classLoader, List.copyOf(shims)));
      return shims.size();
    } catch (RuntimeException | SQLException failure) {
      closeQuietly(classLoader);
      throw failure;
    }
  }

  public synchronized void unregister(String dbType) {
    Loaded previous = loadedByType.remove(dbType);
    if (previous == null) return;
    for (Driver shim : previous.shims()) {
      try {
        DriverManager.deregisterDriver(shim);
      } catch (SQLException ignored) {
        // 注销失败不阻断替换;旧驱动至多残留在 DriverManager 中。
      }
    }
    closeQuietly(previous.classLoader());
  }

  private static void closeQuietly(URLClassLoader classLoader) {
    try {
      classLoader.close();
    } catch (IOException ignored) {
      // 关闭失败仅影响文件句柄释放。
    }
  }
}
