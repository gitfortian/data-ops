package io.yak.ops.business.datasource.management;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DriverJarStore;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** JDBC 驱动包上传命令(Ticket 02):入口校验类型/大小/扩展名,落盘与注册交给存储端口。 */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceDriverManager {

  private final DriverJarStore driverJarStore;
  private final DataSourceProperties properties;

  public String upload(String pluginType, String fileName, byte[] content) {
    DataSourceDbType dbType = parseDbType(pluginType);
    if (fileName == null || !fileName.toLowerCase().endsWith(".jar")) {
      throw new DataSourceException(DataSourceErrorCode.DRIVER_UPLOAD_INVALID);
    }
    if (content == null || content.length == 0) {
      throw new DataSourceException(DataSourceErrorCode.DRIVER_UPLOAD_INVALID);
    }
    if (content.length > properties.getDriver().getMaxFileSizeBytes()) {
      throw new DataSourceException(
          DataSourceErrorCode.DRIVER_UPLOAD_INVALID,
          "驱动包超过上限 " + properties.getDriver().getMaxFileSizeBytes() + " 字节");
    }
    return driverJarStore.store(dbType.name(), fileName, content);
  }

  private static DataSourceDbType parseDbType(String pluginType) {
    try {
      return DataSourceDbType.valueOf(pluginType == null ? "" : pluginType.trim().toUpperCase());
    } catch (IllegalArgumentException failure) {
      throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE);
    }
  }
}
