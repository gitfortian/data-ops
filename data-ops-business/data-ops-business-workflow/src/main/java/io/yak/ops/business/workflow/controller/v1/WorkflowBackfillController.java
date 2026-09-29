package io.yak.ops.business.workflow.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.workflow.backfill.WorkflowBackfillAuditCoordinator;
import io.yak.ops.business.workflow.backfill.WorkflowBackfillManager;
import io.yak.ops.business.workflow.backfill.WorkflowBackfillQuery;
import io.yak.ops.common.bean.dto.workflow.WorkflowBackfillCreateDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBackfillPreviewVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBackfillVO;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 工作流历史补数 / Backfill API。 */
@Tag(name = "工作流补数接口")
@RestController
@RequestMapping("/api/v1/workflows/backfills")
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
@ProjectScope
public class WorkflowBackfillController {
  private final WorkflowBackfillManager service;
  private final WorkflowBackfillAuditCoordinator audit;
  private final WorkflowBackfillQuery query;

  @Autowired
  public WorkflowBackfillController(
      WorkflowBackfillManager service,
      WorkflowBackfillAuditCoordinator audit,
      WorkflowBackfillQuery query) {
    this.service = service;
    this.audit = audit;
    this.query = query;
  }

  /** Focused compatibility constructor for tests without Audit wiring. */
  public WorkflowBackfillController(
      WorkflowBackfillManager service,
      WorkflowBackfillQuery query) {
    this.service = service;
    this.audit = null;
    this.query = query;
  }

  @Operation(summary = "预览 Backfill 将生成的逻辑计划时间")
  @PostMapping("/preview")
  public Result<WorkflowBackfillPreviewVO> preview(
      @Valid @RequestBody WorkflowBackfillCreateDTO request) {
    return Result.success(service.preview(request));
  }

  @Operation(summary = "创建并提交 Backfill 批次")
  @PostMapping
  public Result<WorkflowBackfillVO> create(
      @Valid @RequestBody WorkflowBackfillCreateDTO request) {
    return Result.success(audit == null ? service.create(request) : audit.create(request));
  }

  @Operation(summary = "查询 Backfill 批次")
  @GetMapping
  public Result<List<WorkflowBackfillVO>> list(
      @RequestParam(value = "workflowId", required = false) String workflowId,
      @RequestParam(value = "scheduleId", required = false) String scheduleId,
      @RequestParam(value = "status", required = false) String status) {
    return Result.success(query.list(workflowId, scheduleId, status));
  }

  @Operation(summary = "查询 Backfill 批次详情")
  @GetMapping("/{id}")
  public Result<WorkflowBackfillVO> detail(@PathVariable("id") String id) {
    return Result.success(query.get(id));
  }

  @Operation(summary = "取消 Backfill 中尚未启动的计划")
  @PostMapping("/{id}/cancel")
  public Result<WorkflowBackfillVO> cancel(@PathVariable("id") String id) {
    return Result.success(audit == null ? service.cancel(id) : audit.cancel(id));
  }
}
