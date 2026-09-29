package io.yak.ops.common.bean.po.metric;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 指标标签持久化对象。 */
@Data
@TableName("yak_metric_tag")
public class MetricTagPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;

  private String tagCode;

  private String tagName;

  private Integer sortOrder;

  private String status;

  private String createdBy;

  private String updatedBy;

  private LocalDateTime createTime;

  private LocalDateTime updateTime;
}
