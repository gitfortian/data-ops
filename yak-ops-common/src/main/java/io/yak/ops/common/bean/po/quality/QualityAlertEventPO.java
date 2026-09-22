package io.yak.ops.common.bean.po.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_quality_alert_event")
public class QualityAlertEventPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long monitorId;
  private String executionNo;
  private String checkResult;
  private String alertLevel;
  private String notifyChannel;
  private String notifyTarget;
  private String deliveryStatus;
  private String alertMessage;
  private String errorMessage;
  private LocalDateTime createdAt;
  private LocalDateTime deliveredAt;
}
