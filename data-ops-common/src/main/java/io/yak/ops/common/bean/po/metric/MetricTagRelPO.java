package io.yak.ops.common.bean.po.metric;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 指标标签关联持久化对象。 */
@Data
@TableName("yak_metric_tag_rel")
public class MetricTagRelPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;

  private Long metricId;

  private Long tagId;

  private LocalDateTime createTime;
}
