package io.yak.ops.common.bean.po.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

/** 工作流历史补数批次持久化对象。 */
@Data
@TableName("yak_workflow_backfill")
public class WorkflowBackfillPO {
  @TableId(type = IdType.INPUT)
  private String id;
  private Long projectId;
  private String workflowId;
  private String workflowVersionId;
  private Integer workflowVersionNo;
  private String scheduleId;
  private String scheduleName;
  private String name;
  private String status;
  private String operationType;
  private String sourceExecutionId;
  private LocalDate startBusinessDate;
  private LocalDate endBusinessDate;
  private String cronExpression;
  private String timezone;
  private String executionStrategy;
  private String scheduleInputJson;
  private String inputJson;
  private Integer totalCount;
  private Instant createTime;
  private Instant updateTime;
}
