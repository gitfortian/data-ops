package io.yak.ops.common.bean.vo.workflow;

import java.time.Instant;
import java.util.List;

/** 已发布工作流版本摘要。 */
public record WorkflowVersionVO(
    String id,
    int versionNo,
    boolean active,
    int nodeCount,
    int edgeCount,
    List<TaskBindingVO> taskBindings,
    Instant publishedAt) {

  public record TaskBindingVO(
      String nodeId,
      String taskId,
      String taskName,
      long taskVersion) {
  }
}
