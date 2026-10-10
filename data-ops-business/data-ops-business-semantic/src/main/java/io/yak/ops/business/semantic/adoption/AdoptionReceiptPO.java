package io.yak.ops.business.semantic.adoption;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_semantic_adoption_receipt")
public class AdoptionReceiptPO {
  @TableId(type=IdType.INPUT)
  private String receiptId;
  private Long projectId;
  private String taskId;
  private String candidateId;
  private String payloadDigest;
  private String operatorId;
  private String kind;
  private String status;
  private Long semanticId;
  private Integer semanticVersion;
  private String message;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
