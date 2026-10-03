package io.yak.ops.business.metric.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Current active publication pointer; immutable truth remains MetricVersion + publication event. */
@Data
@TableName("yak_metric_active_publication")
public class MetricActivePublicationPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private Long metricId;
  private Long publicationEventId;
  private Long metricVersionId;
  private Integer metricVersion;
  private String snapshotDigest;
  private String publishedBy;
  private LocalDateTime publishedAt;
  private LocalDateTime updateTime;
}
