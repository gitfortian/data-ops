package io.yak.ops.common.enums.datasource;

import io.yak.framework.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 数据源管理业务错误码。 */
@Getter
@RequiredArgsConstructor
public enum DataSourceErrorCode implements ErrorCode {

  NOT_FOUND(41001, "数据源不存在"),
  DUPLICATE_NAME(41002, "数据源名称已存在"),
  INVALID_DB_TYPE(41003, "数据源类型不合法"),
  INVALID_ENVIRONMENT(41004, "数据源环境不合法"),
  INVALID_CONNECTION_PARAMS(41005, "数据源连接参数不合法"),
  CONNECT_FAILED(41006, "数据源连接测试失败"),
  CREATE_FAILED(41007, "创建数据源失败"),
  UPDATE_FAILED(41008, "更新数据源失败"),
  DELETE_FAILED(41009, "删除数据源失败"),
  QUERY_FAILED(41010, "查询数据源失败"),
  PLUGIN_NOT_FOUND(41011, "数据源插件未安装"),
  CATALOG_FAILED(41012, "读取数据源元数据失败"),
  INVALID_CONNECTION_STATUS(41013, "数据源连接状态不合法"),
  DATASOURCE_REFERENCED(41014, "数据源被其他模块引用，无法删除"),
  DRIVER_UPLOAD_INVALID(41015, "驱动包不合法：需要 .jar 文件且包含 JDBC 驱动实现"),
  DRIVER_UPLOAD_FAILED(41016, "驱动包上传失败"),
  CREDENTIAL_KEY_MISSING(41017, "数据源凭证已静态加密，但未配置解密密钥"),
  CREDENTIAL_CRYPTO_FAILED(41018, "数据源凭证加解密失败：密钥不匹配或密文损坏");

  private final Integer code;
  private final String message;
}
