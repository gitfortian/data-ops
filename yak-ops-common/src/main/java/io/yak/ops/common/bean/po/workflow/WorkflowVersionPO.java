package io.yak.ops.common.bean.po.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** 工作流版本持久化对象。 */
@Data
@TableName("yak_workflow_version")
public class WorkflowVersionPO {
  @TableId(type = IdType.INPUT)
  private String id;
  private Long projectId;
  private String workflowId;
  private Integer versionNo;
  private String versionKind;
  private Long draftRevision;
  private String runRequestJson;
  private String editorMetaJson;
  private String taskVersionsJson;
  private String engineDefinitionJson;
  private String runtimeMetadataJson;
  private Instant createTime;
}
