package io.yak.ops.business.alert.domain;

import io.yak.ops.common.enums.alert.AlertChannelStatus;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/** 告警渠道业务定义；与 MyBatis PO 和 HTTP VO 解耦。 */
@Data
public class AlertChannelDefinition {
  private Long id;
  private String channelType;
  @ToString.Exclude
  private String configJson;
  private Boolean enabled;
  private AlertChannelStatus connStatus;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
