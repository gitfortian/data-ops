package io.yak.ops.common.bean.po.metric;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 指标血缘登记持久化对象(服务层自动写入)。 */
@Data
@TableName("yak_metric_dependency")
public class MetricDependencyPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;

  private Long metricId;

  /** 类型:MODEL/FIELD/CALIBER/UNIT/COMPOSITION。 */
  private String dependencyType;

  /** 依赖对象 ID(松散引用)。 */
  private Long dependencyId;

  /** 依赖对象编码(冗余快照,供展示)。 */
  private String dependencyCode;

  /** 依赖对象版本号(用于影响分析比对)。 */
  private Integer dependencyVersion;

  private LocalDateTime createTime;
}
