package io.yak.ops.business.approval.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 审批流程定义:1~2 级顺序审批,每级静态审批人名单(steps_json)。 */
@Data
@TableName("yak_approval_flow")
public class ApprovalFlowPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private String flowCode;
  private String flowName;
  private String description;
  /** JSON:[{"level":1,"approvers":["a","b"]},...],1~2 级,每级 1~10 人。 */
  private String stepsJson;
  private Boolean enabled;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Boolean deleted;
}
