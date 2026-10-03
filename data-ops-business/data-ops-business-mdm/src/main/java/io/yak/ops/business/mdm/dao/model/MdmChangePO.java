package io.yak.ops.business.mdm.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 主数据变更审批持久化对象(ticket 60)。 */
@Data
@TableName("yak_mdm_change")
public class MdmChangePO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;

  private Long entityId;

  private String masterId;

  private String changeType;

  private String changeContent;

  private Integer approvalLevel;

  private String approvalStatus;

  private String applicant;

  private String approver;

  private String approvalComment;

  private LocalDateTime approvalTime;

  /** 关联审批中心在途/终态单(yak_approval_instance.id,R4)。 */
  private Long instanceId;

  private LocalDateTime createTime;

  private LocalDateTime updateTime;
}
