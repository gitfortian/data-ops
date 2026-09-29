package io.yak.ops.common.bean.dto.alert;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 内部模块发送告警的请求参数。
 *
 * <p>与 {@link AlertSendDTO} 不同，本 DTO 不要求传入完整的渠道配置（如 webhook 地址），
 * 仅需指定告警渠道类型、渠道非地址参数（如@人设置）和告警信息。
 * 持久化的渠道配置由服务层自动合并。
 */
@Data
public class AlertNotifyDTO {

  /** 告警渠道类型（如 DINGTALK），对应 AlertPlugin.type() */
  @NotBlank(message = "渠道类型不能为空")
  private String channelType;

  /**
   * 渠道非地址参数 JSON（可选）。
   *
   * <p>例如钉钉的 {@code atMobiles}、{@code atUserIds}、{@code isAtAll}、{@code msgType} 等。
   * 这些参数会与数据库中持久化的渠道配置合并，传入参数优先级更高。
   * 若为空则完全使用持久化配置。
   */
  private String paramsJson;

  /** 告警标题 */
  @NotBlank(message = "告警标题不能为空")
  private String title;

  /** 告警内容 */
  @NotBlank(message = "告警内容不能为空")
  private String content;

  /** 告警级别：INFO / WARN / ERROR（默认 INFO） */
  private String level;
}
