package io.yak.ops.common.bean.po.metric;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 指标使用记录持久化对象。 */
@Data
@TableName("yak_metric_usage")
public class MetricUsagePO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;

  private Long metricId;

  /** 类型:REPORT/DATASET/DASHBOARD/API/SCREEN(DATASET 为 01 消费接线新增)。 */
  private String usageType;

  /** 使用方 ID。 */
  private Long usageId;

  /** 使用方名称(冗余快照)。 */
  private String usageName;

  private LocalDateTime createTime;
}
