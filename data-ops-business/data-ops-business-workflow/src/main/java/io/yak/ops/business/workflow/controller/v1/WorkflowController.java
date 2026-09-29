package io.yak.ops.business.workflow.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.execution.WorkflowExecutionControlAuditCoordinator;
import io.yak.ops.business.workflow.execution.WorkflowExecutionReactivator;
import io.yak.ops.business.workflow.execution.WorkflowLauncher;
import io.yak.ops.business.workflow.runtime.WorkflowInstanceQueryService;
import io.yak.ops.business.workflow.runtime.WorkflowRuntime;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 工作流运行接口。 */
@Tag(name = "工作流接口")
@RestController
@RequestMapping("/api/v1/workflows")
@ProjectScope
public class WorkflowController {

  private final WorkflowRuntime workflowRuntimeService;
  private final WorkflowLauncher workflowLaunchService;
  private final WorkflowExecutionReactivator workflowReactivationService;
  private final WorkflowExecutionControlAuditCoordinator executionControlAudit;
  private final WorkflowInstanceQueryService workflowInstanceQueryService;

  @Autowired
  public WorkflowController(
      WorkflowRuntime workflowRuntimeService,
      WorkflowLauncher workflowLaunchService,
      WorkflowExecutionReactivator workflowReactivationService,
      WorkflowExecutionControlAuditCoordinator executionControlAudit,
      WorkflowInstanceQueryService workflowInstanceQueryService) {
    this.workflowRuntimeService = workflowRuntimeService;
    this.workflowLaunchService = workflowLaunchService;
    this.workflowReactivationService = workflowReactivationService;
    this.executionControlAudit = executionControlAudit;
    this.workflowInstanceQueryService = workflowInstanceQueryService;
  }

  /** Focused compatibility constructor without query enrichment wiring. */
  public WorkflowController(
      WorkflowRuntime workflowRuntimeService,
      WorkflowLauncher workflowLaunchService,
      WorkflowExecutionReactivator workflowReactivationService,
      WorkflowExecutionControlAuditCoordinator executionControlAudit) {
    this(
        workflowRuntimeService,
        workflowLaunchService,
        workflowReactivationService,
        executionControlAudit,
        null);
  }

  /** Focused compatibility constructor without Audit wiring. */
  public WorkflowController(
      WorkflowRuntime workflowRuntimeService,
      WorkflowLauncher workflowLaunchService,
      WorkflowExecutionReactivator workflowReactivationService) {
    this(
        workflowRuntimeService,
        workflowLaunchService,
        workflowReactivationService,
        null,
        null);
  }

  @Operation(summary = "创建工作流运行实例")
  @PostMapping("/run")
  public Result<WorkflowInstanceVO> run(
      @Valid @RequestBody WorkflowRunDTO request) {
    return Result.success(workflowLaunchService.runAdHoc(request, WorkflowTriggerContext.api()));
  }

  @Operation(summary = "激活工作流运行实例")
  @PostMapping("/instances/{executionId}/activate")
  public Result<WorkflowInstanceVO> activate(
      @PathVariable("executionId") String executionId) {
    return Result.success(workflowRuntimeService.activate(executionId));
  }

  @Operation(summary = "暂停工作流实例")
  @PostMapping("/instances/{executionId}/pause")
  public Result<WorkflowInstanceVO> pause(
      @PathVariable("executionId") String executionId) {
    return Result.success(
        executionControlAudit == null
            ? workflowRuntimeService.pause(executionId)
            : executionControlAudit.pause(executionId));
  }

  @Operation(summary = "恢复工作流实例")
  @PostMapping("/instances/{executionId}/resume")
  public Result<WorkflowInstanceVO> resume(
      @PathVariable("executionId") String executionId) {
    return Result.success(
        executionControlAudit == null
            ? workflowRuntimeService.resume(executionId)
            : executionControlAudit.resume(executionId));
  }

  @Operation(summary = "取消工作流实例")
  @PostMapping("/instances/{executionId}/cancel")
  public Result<WorkflowInstanceVO> cancel(
      @PathVariable("executionId") String executionId) {
    return Result.success(
        executionControlAudit == null
            ? workflowRuntimeService.cancel(executionId)
            : executionControlAudit.cancel(executionId));
  }

  @Operation(summary = "人工放行失败节点并继续执行后续节点")
  @PostMapping("/instances/{executionId}/nodes/{nodeId}/continue")
  public Result<WorkflowInstanceVO> continueAfterFailure(
      @PathVariable("executionId") String executionId,
      @PathVariable("nodeId") String nodeId) {
    return Result.success(workflowReactivationService.continueAfterFailure(executionId, nodeId));
  }

  @Operation(summary = "重新执行当前失败节点")
  @PostMapping("/instances/{executionId}/nodes/{nodeId}/retry")
  public Result<WorkflowInstanceVO> retryFailedNode(
      @PathVariable("executionId") String executionId,
      @PathVariable("nodeId") String nodeId) {
    return Result.success(workflowReactivationService.retryFailedNode(executionId, nodeId));
  }

  @Operation(summary = "重试实例中的失败节点")
  @PostMapping("/instances/{executionId}/retry-failed")
  public Result<WorkflowInstanceVO> retryFailedNodes(
      @PathVariable("executionId") String executionId) {
    return Result.success(workflowReactivationService.retryFailedNodes(executionId));
  }

  @Operation(summary = "重新运行整个工作流")
  @PostMapping("/instances/{executionId}/restart")
  public Result<WorkflowInstanceVO> restart(
      @PathVariable("executionId") String executionId) {
    return Result.success(
        workflowLaunchService.restart(executionId, WorkflowTriggerContext.manual()));
  }

  @Operation(summary = "从指定节点重新运行")
  @PostMapping("/instances/{executionId}/nodes/{nodeId}/rerun")
  public Result<WorkflowInstanceVO> rerunFromNode(
      @PathVariable("executionId") String executionId,
      @PathVariable("nodeId") String nodeId) {
    return Result.success(
        workflowLaunchService.rerunFromNode(
            executionId,
            nodeId,
            WorkflowTriggerContext.manual()));
  }

  @Operation(summary = "查询工作流实例")
  @GetMapping("/instances")
  public Result<List<WorkflowInstanceVO>> instances() {
    return Result.success(
        workflowInstanceQueryService == null
            ? workflowRuntimeService.listInstances()
            : workflowInstanceQueryService.listInstances());
  }

  @Operation(summary = "查询工作流实例详情")
  @GetMapping("/instances/{executionId}")
  public Result<WorkflowInstanceVO> instance(
      @PathVariable("executionId") String executionId) {
    return Result.success(workflowRuntimeService.getInstance(executionId));
  }

  @Operation(summary = "订阅工作流实例实时状态")
  @GetMapping(
      value = "/instances/{executionId}/events",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(
      @PathVariable("executionId") String executionId) {
    return workflowRuntimeService.subscribe(executionId);
  }
}
