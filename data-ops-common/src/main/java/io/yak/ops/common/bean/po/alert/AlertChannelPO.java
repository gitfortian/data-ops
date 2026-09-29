package io.yak.ops.common.bean.po.alert;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.alert.AlertChannelStatus;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/** 告警渠道配置持久化对象。 */
@Data
@TableName("yak_ops_alert_channel")
public class AlertChannelPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 渠道类型标识（如 DINGTALK），对应 AlertPlugin.type()。 */
  private String channelType;

  /** 渠道配置 JSON。 */
  @ToString.Exclude
  private String configJson;

  /** 是否启用。 */
  private Boolean enabled;

  /** 连通状态。 */
  private AlertChannelStatus connStatus;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
