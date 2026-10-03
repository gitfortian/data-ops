package io.yak.ops.business.metric.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 复合指标组成持久化对象。 */
@Data
@TableName("yak_metric_composition")
public class MetricCompositionPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;

  /** 复合指标 ID。 */
  private Long metricId;

  /** 子指标 ID。 */
  private Long subMetricId;

  /** 运算方式:ADD/SUB/MUL/DIV。 */
  private String operator;

  /** 完整表达式(可选)。 */
  private String expression;

  /** 操作数顺序。 */
  private Integer sortOrder;

  private LocalDateTime createTime;
}
