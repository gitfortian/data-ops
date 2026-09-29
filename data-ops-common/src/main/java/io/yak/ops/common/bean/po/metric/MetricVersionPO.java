package io.yak.ops.common.bean.po.metric;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 指标版本历史持久化对象。 */
@Data
@TableName("yak_metric_version")
public class MetricVersionPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;

  private Long metricId;

  private Integer version;

  /** 版本快照(JSON)。 */
  private String snapshot;

  /** 变更说明。 */
  private String changeDesc;

  private String changedBy;

  private LocalDateTime createTime;
}
