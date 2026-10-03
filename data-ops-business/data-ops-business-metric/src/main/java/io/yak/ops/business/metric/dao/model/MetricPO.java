package io.yak.ops.business.metric.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 指标主表持久化对象。 */
@Data
@TableName("yak_metric")
public class MetricPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;

  /** 指标编码,项目内唯一,创建后不可改。 */
  private String metricCode;

  private String metricName;

  /** 业务域(引用 semantic,松散 ID)。 */
  private Long domainId;

  /** 业务过程引用(原子必填,派生可选)。 */
  private Long processId;

  /** 类型:ATOMIC/DERIVED/COMPOSITE。 */
  private String metricType;

  /** 口径标准引用(引用 semantic,松散 ID,可空)。 */
  private Long caliberId;

  /** 计算规则(从口径标准带出或手填)。 */
  private String calRule;

  /** 度量表达式,如 SUM(order_amount)。 */
  private String measureExpr;

  /** 过滤条件,如 order_status != '已取消'。 */
  private String filterExpr;

  /** DIM模型引用(JSON 数组)。 */
  private String dimModelIds;

  /** 引用的原子指标 ID(仅派生指标)。 */
  private Long refMetricId;

  /** 维度限定(仅派生指标)。 */
  private String dimConstraint;

  /** 结构化限定条件(JSON 数组,仅派生指标;02 起,空=存量自由文本登记式)。 */
  private String qualifiersJson;

  /** 依赖模型(引用 modeling,松散 ID)。 */
  private Long modelId;

  /** 统计维度(JSON 数组)。 */
  private String statDimensions;

  /** 统计周期:DAY/WEEK/MONTH。 */
  private String statPeriod;

  /** 单位标准引用(引用 semantic,松散 ID)。 */
  private Long unitId;

  /** 业务口径描述。 */
  private String businessDesc;

  /** 负责人。 */
  private String owner;

  /** 状态:ENABLED/DISABLED。 */
  private String status;

  /** 乐观锁版本。 */
  private Integer version;

  private String createdBy;

  private String updatedBy;

  private LocalDateTime createTime;

  private LocalDateTime updateTime;
}
