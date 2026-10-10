package io.yak.ops.business.agent.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.agent.AgentPermissionCode;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.conversation.SourceSemanticTaskFacade;
import io.yak.ops.business.agent.conversation.SourceSemanticTaskFacade.ColumnChoice;
import io.yak.ops.business.agent.conversation.SourceSemanticTaskFacade.Create;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Source schema analysis is NOT enabled by default. Both CHAT_RUN route permission
 * and the explicit Datasource READ permission in Facade are required for every read/write.
 */
@Tag(name = "F-039 来源结构业务理解（需显式启用）")
@RestController
@RequiredArgsConstructor
@ConditionalOnAgentEnabled
@ConditionalOnProperty(prefix = "yak.agent.source-semantic", name = "enabled", havingValue = "true")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(AgentPermissionCode.CHAT_RUN)
@RequestMapping("/api/v1/agent/source-semantic")
public class SourceSemanticTaskController {
  private final SourceSemanticTaskFacade service;

  public record PreviewRequest(String dataSourceId, List<ColumnChoice> selection) {}
  public record ApproveRequest(String reviewedPlanSha256) {}

  @Operation(summary = "按项目/用户权限浏览已采集来源表")
  @GetMapping("/tables")
  public Result<List<Map<String, String>>> tables(
      @RequestParam String dataSourceId,
      @RequestParam(defaultValue = "1") int page) {
    return Result.success(service.tables(dataSourceId, page));
  }

  @Operation(summary = "完整列证据与分片范围预览（不读取源数据行）")
  @PostMapping("/preview")
  public Result<SourceSemanticTaskFacade.Preview> preview(@RequestBody PreviewRequest request) {
    return Result.success(service.preview(request.dataSourceId(), request.selection()));
  }

  @Operation(summary = "持久化来源范围和人工审核的 PLAN.md")
  @PostMapping("/tasks")
  public Result<SourceSemanticTaskFacade.TaskView> create(@Valid @RequestBody Create request) {
    return Result.success(service.create(request));
  }

  @Operation(summary = "恢复读取任务、真实原 turn 状态、结果覆盖与计划")
  @GetMapping("/tasks/{taskId}")
  public Result<SourceSemanticTaskFacade.TaskView> read(@PathVariable String taskId) {
    return Result.success(service.read(taskId));
  }

  @Operation(summary = "按用户核对的完整 PLAN.md SHA-256 批准")
  @PostMapping("/tasks/{taskId}/approve")
  public Result<SourceSemanticTaskFacade.TaskView> approve(@PathVariable String taskId,
      @RequestBody ApproveRequest request) {
    return Result.success(service.approve(taskId, request.reviewedPlanSha256()));
  }

  @Operation(summary = "CAS 冻结下一分片并通过原 AgentTurn 入队")
  @PostMapping("/tasks/{taskId}/next")
  public Result<SourceSemanticTaskFacade.TaskView> next(@PathVariable String taskId) {
    return Result.success(service.next(taskId));
  }

  @Operation(summary = "暂停尚未派出的后续分片")
  @PostMapping("/tasks/{taskId}/pause")
  public Result<SourceSemanticTaskFacade.TaskView> pause(@PathVariable String taskId) {
    return Result.success(service.pause(taskId));
  }

  @Operation(summary = "重新核验来源和 PLAN 后恢复后续分片")
  @PostMapping("/tasks/{taskId}/resume")
  public Result<SourceSemanticTaskFacade.TaskView> resume(@PathVariable String taskId) {
    return Result.success(service.resume(taskId));
  }

  @Operation(summary = "CAS 封锁任务后精确停止原 turn")
  @PostMapping("/tasks/{taskId}/cancel")
  public Result<SourceSemanticTaskFacade.TaskView> cancel(@PathVariable String taskId) {
    return Result.success(service.cancel(taskId));
  }

  @Operation(summary = "按原 turn 和片段查已验证不可变分析文本")
  @GetMapping("/tasks/{taskId}/artifacts/{chunkId}")
  public Result<SourceSemanticTaskFacade.Artifact> artifact(
      @PathVariable String taskId, @PathVariable String chunkId) {
    return Result.success(service.artifact(taskId, chunkId));
  }
}
