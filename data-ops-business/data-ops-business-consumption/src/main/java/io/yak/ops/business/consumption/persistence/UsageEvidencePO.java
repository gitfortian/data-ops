package io.yak.ops.business.consumption.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_ops_consumption_usage_evidence")
public class UsageEvidencePO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String productKey;
  private String sourceVersionIdentity;
  private String sourceDisplayVersion;
  private String consumerType;
  private String sourceDomain;
  private String sourceIdentity;
  private String displayHint;
  private LocalDateTime observedAt;
  private String consumptionMode;
  private String outcome;
  private String provider;
  private String providerEvidenceRef;
  private String deduplicationId;
  private LocalDateTime normalizedAt;
}
