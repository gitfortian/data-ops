package io.yak.ops.common.bean.dto.alert;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 保存告警渠道配置的请求参数。 */
@Data
public class AlertChannelSaveDTO {

  /** 告警渠道类型（如 DINGTALK），对应 AlertPlugin.type()。 */
  @NotBlank(message = "渠道类型不能为空")
  private String channelType;

  /** 告警渠道配置 JSON。 */
  @NotBlank(message = "渠道配置不能为空")
  private String configJson;

  /** 是否启用。 */
  private Boolean enabled;
}
