package io.yak.ops.common.enums.alert;

import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 告警渠道连通状态。 */
@Getter
@RequiredArgsConstructor
public enum AlertChannelStatus {

  UNKNOWN("未测试"),
  CONNECTED("连接可用"),
  DISCONNECTED("连接不可用");

  private final String displayName;

  public static AlertChannelStatus parse(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("告警渠道连通状态不能为空");
    }
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
