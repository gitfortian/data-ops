package io.yak.ops.common.bean.po.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;

@Data
@TableName("yak_quality_monitor_setting")
public class QualityMonitorSettingPO {
  @TableId(type = IdType.INPUT)
  private Long monitorId;
  private String runMode;
  private String scheduleFrequency;
  private LocalTime scheduleTime;
  private String scheduleWeekday;
  private String cronExpression;
  private LocalDateTime nextRunTime;
  private String ruleFailureAction;
  private Boolean notifyEnabled;
  private String notifyChannel;
  private String notifyTarget;
  private String alertLevel;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
