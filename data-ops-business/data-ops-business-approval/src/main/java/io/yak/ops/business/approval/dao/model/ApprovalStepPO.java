package io.yak.ops.business.approval.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 审批级快照行 = 审批记录(D3,不另设历史表)。发起时按 flow 配置逐人展开(D4);
 * 同级任一人处理即定级(ANY,D7),同侪置 SKIPPED。
 */
@Data
@TableName("yak_approval_step")
public class ApprovalStepPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private Long instanceId;
  /** 1 起。 */
  private Integer levelNo;
  private String approver;
  private String status;
  private String comment;
  private LocalDateTime handledTime;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Boolean deleted;
}
