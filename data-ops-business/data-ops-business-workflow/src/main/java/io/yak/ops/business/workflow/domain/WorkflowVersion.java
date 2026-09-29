package io.yak.ops.business.workflow.domain;

import io.yak.ops.business.job.task.TaskVersionSnapshot;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 工作流一次正式发布形成的不可变执行与编辑快照。 */
public record WorkflowVersion(
    String id,
    String workflowId,
    int versionNo,
    long draftRevision,
    WorkflowRunSpec runSpec,
    Map<String, Object> editorMeta,
    Map<String, TaskVersionSnapshot> taskVersionsByNode,
    Instant publishedAt) {

  public WorkflowVersion {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("工作流版本 ID 不能为空");
    if (workflowId == null || workflowId.isBlank()) throw new IllegalArgumentException("工作流 ID 不能为空");
    if (versionNo < 1) throw new IllegalArgumentException("工作流版本号必须大于 0");
    if (runSpec == null) throw new IllegalArgumentException("工作流版本运行快照不能为空");
    editorMeta = editorMeta == null
        ? Map.of()
        : Map.copyOf(new LinkedHashMap<>(editorMeta));
    taskVersionsByNode = taskVersionsByNode == null
        ? Map.of()
        : Map.copyOf(new LinkedHashMap<>(taskVersionsByNode));
    publishedAt = publishedAt == null ? Instant.now() : publishedAt;
  }
}
