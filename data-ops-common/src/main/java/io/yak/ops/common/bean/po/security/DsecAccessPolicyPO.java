package io.yak.ops.common.bean.po.security;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据访问策略持久化对象。 */
@Data
@TableName("yak_dsec_access_policy")
public class DsecAccessPolicyPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String policyName;
  private String subjectType;
  private String subjectKey;
  private String scopeType;
  private Long datasourceId;
  private String dbName;
  private String tableName;
  private String columnName;
  private Long levelId;
  private String accessType;
  private String effect;
  private Integer priority;
  private LocalDateTime validFrom;
  private LocalDateTime validTo;
  private String status;
  private String applicant;
  private String approver;
  private String reason;
  private String createdBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
