package io.yak.ops.business.consumption.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_ops_consumption_subscription")
public class SubscriptionPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String productKey;
  private String consumerType;
  private String sourceDomain;
  private String sourceIdentity;
  private String displayHint;
  private String consumptionMode;
  private String status;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
}
