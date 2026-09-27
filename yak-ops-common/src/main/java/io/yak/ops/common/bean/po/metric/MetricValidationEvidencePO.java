package io.yak.ops.common.bean.po.metric;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 指标定义校验证据；每条记录绑定一个不可变 MetricVersion。 */
@Data
@TableName("yak_metric_validation_evidence")
public class MetricValidationEvidencePO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;

  private Long metricId;

  /** yak_metric_version.id，不可变版本身份。 */
  private Long metricVersionId;

  /** 冗余版本号，便于查询与审计展示。 */
  private Integer metricVersion;

  /** READY / BLOCKED。 */
  private String result;

  /** 结构化 ValidationIssue 数组 JSON。 */
  private String issuesJson;

  /** 校验实现身份，例如 metric-definition-validator/v1。 */
  private String provider;

  /** 被校验版本快照的 SHA-256，证明 evidence 对应的 immutable payload。 */
  private String snapshotDigest;

  private String checkedBy;

  private LocalDateTime checkedAt;
}
