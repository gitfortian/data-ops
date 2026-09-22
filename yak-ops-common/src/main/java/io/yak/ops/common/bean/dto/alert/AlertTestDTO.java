package io.yak.ops.common.bean.dto.alert;

import lombok.Data;

/** 测试告警渠道连通性的请求参数。 */
@Data
public class AlertTestDTO {

  /** 告警渠道类型（如 DINGTALK），对应 AlertPlugin.type() */
  private String channelType;

  /** 告警渠道配置 JSON（如 webhookUrl、secret 等） */
  private String configJson;
}
