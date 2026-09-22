package io.yak.ops.common.bean.dto.alert;

import lombok.Data;

/** 发送告警的请求参数。 */
@Data
public class AlertSendDTO {

  /** 告警渠道类型（如 DINGTALK），对应 AlertPlugin.type() */
  private String channelType;

  /** 告警渠道配置 JSON（如 webhookUrl、secret 等） */
  private String configJson;

  /** 告警标题 */
  private String title;

  /** 告警内容 */
  private String content;

  /** 告警级别：INFO / WARN / ERROR */
  private String level;
}
