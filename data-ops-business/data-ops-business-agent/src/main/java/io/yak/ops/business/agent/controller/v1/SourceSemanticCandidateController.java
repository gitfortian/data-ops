package io.yak.ops.business.agent.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.agent.AgentPermissionCode;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.conversation.SourceSemanticCandidateService;
import io.yak.ops.business.agent.conversation.SourceSemanticCandidateService.Answer;
import io.yak.ops.business.agent.conversation.SourceSemanticCandidateService.Edit;
import io.yak.ops.business.agent.conversation.SourceSemanticCandidateService.Selection;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@Tag(name = "F-039 集中语义候选复核（只读预检）")
@RestController
@RequiredArgsConstructor
@ConditionalOnAgentEnabled
@ConditionalOnProperty(prefix = "yak.agent.source-semantic", name = "enabled", havingValue = "true")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(AgentPermissionCode.CHAT_RUN)
@RequestMapping("/api/v1/agent/source-semantic/tasks/{taskId}/candidates")
public class SourceSemanticCandidateController {
  private final SourceSemanticCandidateService service;

  @Operation(summary = "读取或初始化已完成任务的只读候选草稿")
  @GetMapping
  public Result<SourceSemanticCandidateService.View> read(@PathVariable String taskId) {
    return Result.success(service.read(taskId));
  }
  @Operation(summary = "人工修订候选；修改引用/名称使旧预检失效")
  @PostMapping("/edit")
  public Result<SourceSemanticCandidateService.View> edit(
      @PathVariable String taskId,@RequestBody Edit request) {
    return Result.success(service.edit(taskId,request));
  }
  @Operation(summary = "集中选择候选，不自动选择任何依赖")
  @PostMapping("/select")
  public Result<SourceSemanticCandidateService.View> select(
      @PathVariable String taskId,@RequestBody Selection request) {
    return Result.success(service.select(taskId,request));
  }
  @Operation(summary = "集中人工答疑，生成新审核修订")
  @PostMapping("/answer")
  public Result<SourceSemanticCandidateService.View> answer(
      @PathVariable String taskId,@RequestBody Answer request) {
    return Result.success(service.answer(taskId,request));
  }
  @Operation(summary = "跨项目/来源/标准目录新鲜校验与依赖预检（零业务写入）")
  @PostMapping("/preflight")
  public Result<SourceSemanticCandidateService.Preflight> preflight(
      @PathVariable String taskId,@RequestParam long revision) {
    return Result.success(service.preflight(taskId,revision));
  }
}
