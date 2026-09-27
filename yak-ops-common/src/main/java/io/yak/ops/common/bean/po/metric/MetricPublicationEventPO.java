package io.yak.ops.common.bean.po.metric;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Append-only publication lifecycle event bound to an immutable MetricVersion. */
@Data
@TableName("yak_metric_publication_event")
public class MetricPublicationEventPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private Long metricId;
  private Long metricVersionId;
  private Integer metricVersion;
  private String snapshotDigest;
  /** PUBLISHED / WITHDRAWN. */
  private String eventType;
  /** For WITHDRAWN, points at the PUBLISHED event that was active. */
  private Long subjectPublicationId;
  /** Frozen publication-readiness gate evidence JSON for PUBLISHED events. */
  private String readinessJson;
  private String actedBy;
  private LocalDateTime actedAt;
}
