package io.yak.ops.business.workflow.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.workflow.execution.WorkflowExecutionManager;
import io.yak.ops.common.bean.dto.workflow.WorkflowBatchRetryDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowBusinessDateRerunDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBackfillVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBatchRetryVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceOperationsVO;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作流实例运维与补跑接口。 */
@Tag(name = "工作流实例运维")
@RestController
@RequestMapping("/api/v1/workflows/instances")
@ProjectScope
public class WorkflowInstanceOperationsController {
  private final WorkflowExecutionManager operations;

  public WorkflowInstanceOperationsController(WorkflowExecutionManager operations) {
    this.operations = operations;
  }

  @Operation(summary = "查询实例运维上下文与运行 DAG 拓扑")
  @GetMapping("/{executionId}/operations")
  public Result<WorkflowInstanceOperationsVO> describe(
      @PathVariable("executionId") String executionId) {
    return Result.success(operations.describe(executionId));
  }

  @Operation(summary = "按指定 businessDate 重跑来源实例的固定发布版本")
  @PostMapping("/{executionId}/rerun-business-date")
  public Result<WorkflowBackfillVO> rerunBusinessDate(
      @PathVariable("executionId") String executionId,
      @Valid @RequestBody WorkflowBusinessDateRerunDTO request) {
    return Result.success(operations.rerunBusinessDate(executionId, request));
  }

  @Operation(summary = "批量重试失败工作流实例")
  @PostMapping("/batch-retry-failed")
  public Result<WorkflowBatchRetryVO> batchRetryFailed(
      @Valid @RequestBody WorkflowBatchRetryDTO request) {
    return Result.success(operations.batchRetryFailed(request));
  }
}
