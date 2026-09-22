package io.yak.ops.common.bean.vo.workflow;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 工作流定义展示对象。 */
public record WorkflowDefinitionVO(
    String id,
    String name,
    String description,
    String status,
    int nodeCount,
    int edgeCount,
    List<NodeVO> nodes,
    List<EdgeVO> edges,
    Map<String, Object> input,
    Map<String, Object> editorMeta,
    long workflowTimeoutSeconds,
    String failureStrategy,
    String activeVersionId,
    Integer activeVersionNo,
    int latestVersionNo,
    boolean draftChanged,
    String latestExecutionId,
    String latestExecutionStatus,
    Instant createTime,
    Instant updateTime) {

  public record NodeVO(
      String id,
      String taskId,
      @JsonSerialize(using = ToStringSerializer.class) Long taskAssetId,
      @JsonSerialize(using = ToStringSerializer.class) Long taskRevisionId,
      Integer taskRevisionNo,
      String taskAssetName,
      String taskType,
      String taskAssetStatus,
      @JsonSerialize(using = ToStringSerializer.class) Long latestTaskRevisionId,
      Integer latestTaskRevisionNo,
      boolean taskRevisionUpdateAvailable,
      double positionX,
      double positionY,
      int maxAttempts,
      long retryDelaySeconds,
      long dispatchTimeoutSeconds,
      long executionTimeoutSeconds,
      Map<String, String> inputMapping,
      String triggerRule,
      String failurePolicy) {
  }

  public record EdgeVO(String source, String target) {
  }
}
