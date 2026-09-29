package io.yak.ops.business.workflow.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.workflow.definition.WorkflowDefinitionAuditCoordinator;
import io.yak.ops.business.workflow.definition.WorkflowDefinitionManager;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.execution.WorkflowLauncher;
import io.yak.ops.business.workflow.schedule.WorkflowDefinitionScheduleGuard;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionCreateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowVersionVO;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 工作流定义管理接口。 */
@Tag(name = "工作流定义接口")
@RestController
@RequestMapping("/api/v1/workflows/definitions")
@ProjectScope
public class WorkflowDefinitionController {

  private final WorkflowDefinitionManager definitionService;
  private final WorkflowDefinitionAuditCoordinator definitionAudit;
  private final WorkflowLauncher launchService;
  private final WorkflowDefinitionScheduleGuard scheduleGuard;

  @Autowired
  public WorkflowDefinitionController(
      WorkflowDefinitionManager definitionService,
      WorkflowDefinitionAuditCoordinator definitionAudit,
      WorkflowLauncher launchService,
      WorkflowDefinitionScheduleGuard scheduleGuard) {
    this.definitionService = definitionService;
    this.definitionAudit = definitionAudit;
    this.launchService = launchService;
    this.scheduleGuard = scheduleGuard;
  }

  /** Focused compatibility constructor for tests that do not wire business audit. */
  public WorkflowDefinitionController(
      WorkflowDefinitionManager definitionService,
      WorkflowLauncher launchService,
      WorkflowDefinitionScheduleGuard scheduleGuard) {
    this.definitionService = definitionService;
    this.definitionAudit = null;
    this.launchService = launchService;
    this.scheduleGuard = scheduleGuard;
  }

  @Operation(summary = "查询工作流定义")
  @GetMapping
  public Result<List<WorkflowDefinitionVO>> list(
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "status", required = false) String status) {
    return Result.success(definitionService.list(keyword, status));
  }

  @Operation(summary = "创建工作流草稿")
  @PostMapping
  public Result<WorkflowDefinitionVO> create(
      @Valid @RequestBody WorkflowDefinitionCreateDTO request) {
    return Result.success(
        definitionAudit == null ? definitionService.create(request) : definitionAudit.create(request));
  }

  @Operation(summary = "查询工作流定义详情")
  @GetMapping("/{id}")
  public Result<WorkflowDefinitionVO> detail(@PathVariable("id") String id) {
    return Result.success(definitionService.get(id));
  }

  @Operation(summary = "保存工作流草稿")
  @PutMapping("/{id}")
  public Result<WorkflowDefinitionVO> update(
      @PathVariable("id") String id,
      @Valid @RequestBody WorkflowDefinitionUpdateDTO request) {
    return Result.success(
        definitionAudit == null
            ? definitionService.update(id, request)
            : definitionAudit.update(id, request));
  }

  @Operation(summary = "显式升级工作流节点到任务资产最新版本")
  @PostMapping("/{id}/nodes/{nodeId}/upgrade-task-revision")
  public Result<WorkflowDefinitionVO> upgradeTaskRevision(
      @PathVariable("id") String id,
      @PathVariable("nodeId") String nodeId) {
    return Result.success(
        definitionAudit == null
            ? definitionService.upgradeTaskRevision(id, nodeId)
            : definitionAudit.upgradeTaskRevision(id, nodeId));
  }

  @Operation(summary = "删除工作流定义")
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") String id) {
    if (definitionAudit == null) definitionService.delete(id);
    else definitionAudit.delete(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "发布或重新启用工作流版本")
  @PostMapping("/{id}/online")
  public Result<WorkflowDefinitionVO> online(@PathVariable("id") String id) {
    WorkflowDefinitionVO workflow =
        definitionAudit == null ? definitionService.online(id) : definitionAudit.online(id);
    scheduleGuard.activateConfiguredSchedules(id);
    return Result.success(workflow);
  }

  @Operation(summary = "停用工作流正式运行入口")
  @PostMapping("/{id}/offline")
  public Result<WorkflowDefinitionVO> offline(@PathVariable("id") String id) {
    scheduleGuard.deactivateConfiguredSchedules(id);
    return Result.success(
        definitionAudit == null ? definitionService.offline(id) : definitionAudit.offline(id));
  }

  @Operation(summary = "执行当前启用的已发布版本")
  @PostMapping("/{id}/run")
  public Result<WorkflowDefinitionVO> run(@PathVariable("id") String id) {
    return Result.success(launchService.runPublished(id, WorkflowTriggerContext.manual()));
  }

  @Operation(summary = "测试运行当前工作流草稿")
  @PostMapping("/{id}/test-run")
  public Result<WorkflowDefinitionVO> testRun(@PathVariable("id") String id) {
    return Result.success(launchService.testRunDraft(id, WorkflowTriggerContext.manual()));
  }

  @Operation(summary = "查询工作流发布版本")
  @GetMapping("/{id}/versions")
  public Result<List<WorkflowVersionVO>> versions(@PathVariable("id") String id) {
    return Result.success(definitionService.versions(id));
  }

  @Operation(summary = "查看单版本详情（diff 数据源）")
  @GetMapping("/{id}/versions/{versionNo}")
  public Result<WorkflowDefinitionManager.WorkflowVersionDetailView> version(
      @PathVariable("id") String id, @PathVariable("versionNo") int versionNo) {
    return Result.success(definitionService.versionDetail(id, versionNo));
  }

  @Operation(summary = "回滚到指定版本（追加式：以该版本内容生成新版本并启用）")
  @PostMapping("/{id}/versions/{versionNo}/rollback")
  public Result<WorkflowDefinitionVO> rollback(
      @PathVariable("id") String id, @PathVariable("versionNo") int versionNo) {
    WorkflowDefinitionVO workflow = definitionService.rollback(id, versionNo);
    scheduleGuard.activateConfiguredSchedules(id);
    return Result.success(workflow);
  }

  @Operation(summary = "暂停工作流最近一次执行")
  @PostMapping("/{id}/pause")
  public Result<WorkflowDefinitionVO> pause(@PathVariable("id") String id) {
    return Result.success(
        definitionAudit == null
            ? definitionService.pause(id)
            : definitionAudit.pauseExecution(id));
  }

  @Operation(summary = "恢复工作流最近一次执行")
  @PostMapping("/{id}/resume")
  public Result<WorkflowDefinitionVO> resume(@PathVariable("id") String id) {
    return Result.success(
        definitionAudit == null
            ? definitionService.resume(id)
            : definitionAudit.resumeExecution(id));
  }
}
