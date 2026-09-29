package io.yak.ops.common.bean.vo.alert;

import io.yak.ops.plugin.alert.api.AlertPluginDescriptor;
import lombok.Data;

/** 告警渠道摘要信息。 */
@Data
public class AlertChannelVO {

  /** 持久化渠道主键；尚未保存配置的插件描述为 null。 */
  private Long id;

  /** 渠道类型标识 */
  private String type;

  /** 渠道显示名称 */
  private String name;

  /** 渠道描述 */
  private String description;

  /** 插件版本 */
  private String version;

  /** 是否启用 */
  private Boolean enabled;

  /** 连通状态 */
  private String connStatus;

  /** 渠道配置 JSON（仅详情接口返回） */
  private String configJson;

  /** 从 SPI 插件描述符创建（无持久化配置时的默认值）。 */
  public static AlertChannelVO from(AlertPluginDescriptor descriptor) {
    AlertChannelVO vo = new AlertChannelVO();
    vo.setType(descriptor.type());
    vo.setName(descriptor.name());
    vo.setDescription(descriptor.description());
    vo.setVersion(descriptor.version());
    vo.setEnabled(false);
    vo.setConnStatus("UNKNOWN");
    vo.setConfigJson(null);
    return vo;
  }
}
