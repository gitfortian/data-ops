package io.yak.ops.common.bean.po.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** 工作流定义持久化对象。 */
@Data
@TableName("yak_workflow_definition")
public class WorkflowDefinitionPO {
  @TableId(type = IdType.INPUT)
  private String id;
  private Long projectId;
  private String name;
  private String description;
  private String status;
  private Long draftRevision;
  private Integer latestVersionNo;
  private String activeVersionId;
  private String draftJson;
  private String latestExecutionId;
  private String latestExecutionStatus;
  private Instant createTime;
  private Instant updateTime;
}
