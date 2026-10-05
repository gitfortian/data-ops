package io.yak.ops.business.approval.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 审批单。在途唯一(D6):active_flag 在 PENDING 期='Y',终态置 NULL;
 * 唯一键 (project_id, flow_code, biz_type, biz_id, active_flag) 借 MySQL 多 NULL 并存。
 */
@Data
@TableName("yak_approval_instance")
public class ApprovalInstancePO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private String flowCode;
  /** 发起时快照,流程改名不影响在途单展示。 */
  private String flowName;
  private String bizType;
  private String bizId;
  private String title;
  /** 审批依据快照 JSON(≤64KB),审批人只读;业务真相在业务侧。 */
  private String payloadJson;
  private String applicant;
  private String status;
  private Integer currentLevel;
  private String activeFlag;
  private LocalDateTime finishTime;
  private String cancelReason;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Boolean deleted;
}
