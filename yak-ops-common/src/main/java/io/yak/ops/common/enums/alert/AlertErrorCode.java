package io.yak.ops.common.enums.alert;

import io.yak.framework.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 告警服务业务错误码。 */
@Getter
@RequiredArgsConstructor
public enum AlertErrorCode implements ErrorCode {

  PLUGIN_NOT_FOUND(46001, "告警插件未安装"),
  INVALID_CHANNEL_TYPE(46002, "告警渠道类型不合法"),
  INVALID_CONFIG(46003, "告警渠道配置不合法"),
  SEND_FAILED(46004, "告警发送失败"),
  TEST_CONNECTION_FAILED(46005, "告警渠道连通性测试失败"),
  CHANNEL_NOT_FOUND(46006, "告警渠道不存在");

  private final Integer code;
  private final String message;
}
