package io.yak.ops.business.security.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 脱敏策略持久化对象。 */
@Data
@TableName("yak_dsec_masking_policy")
public class DsecMaskingPolicyPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String policyName;
  private Long levelId;
  private Long categoryId;
  private String columnPattern;
  private Long algoId;
  private Integer priority;
  private Integer enabled;
  private String description;
  private String createdBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
