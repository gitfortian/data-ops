package io.yak.ops.business.approval.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.approval.application.FlowAdminService;
import io.yak.ops.business.approval.application.FlowAdminService.FlowView;
import io.yak.ops.business.approval.controller.v1.dto.ApprovalRequests.FlowCreateDTO;
import io.yak.ops.business.approval.controller.v1.dto.ApprovalRequests.FlowStepDTO;
import io.yak.ops.business.approval.controller.v1.dto.ApprovalRequests.FlowUpdateDTO;
import io.yak.ops.common.constant.approval.ApprovalPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 审批流程定义 REST API(ticket 102)。 */
@Tag(name = "审批中心-流程定义")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/approvals/flows")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ApprovalPermissionCode.READ)
public class ApprovalFlowController {

  private final FlowAdminService flowAdminService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "流程定义列表")
  @GetMapping
  public Result<List<FlowView>> list(
      @RequestParam(value = "keyword", required = false) String keyword) {
    return Result.success(flowAdminService.list(keyword));
  }

  @Operation(summary = "流程定义详情")
  @GetMapping("/{id}")
  public Result<FlowView> get(@PathVariable("id") Long id) {
    return Result.success(flowAdminService.get(id));
  }

  @Operation(summary = "新建流程定义(默认启用)")
  @RequiresPermission(ApprovalPermissionCode.CREATE)
  @PostMapping
  public Result<FlowView> create(
      @Valid @RequestBody FlowCreateDTO dto, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(flowAdminService.create(dto.getFlowCode(), dto.getFlowName(),
        dto.getDescription(), approverLevels(dto.getSteps()), operator));
  }

  @Operation(summary = "编辑流程定义(flowCode 不可变)")
  @RequiresPermission(ApprovalPermissionCode.MANAGE)
  @PutMapping("/{id}")
  public Result<FlowView> update(
      @PathVariable("id") Long id,
      @Valid @RequestBody FlowUpdateDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(flowAdminService.update(id, dto.getFlowName(), dto.getDescription(),
        approverLevels(dto.getSteps()), operator));
  }

  @Operation(summary = "启用/停用流程定义")
  @RequiresPermission(ApprovalPermissionCode.MANAGE)
  @PutMapping("/{id}/toggle")
  public Result<FlowView> toggle(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(flowAdminService.toggle(id, operator));
  }

  @Operation(summary = "删除流程定义(软删并释放编码;有在途审批单时拒绝)")
  @RequiresPermission(ApprovalPermissionCode.MANAGE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    flowAdminService.delete(id, operator);
    return Result.success(Boolean.TRUE);
  }

  private static List<List<String>> approverLevels(List<FlowStepDTO> steps) {
    return steps == null ? List.of()
        : steps.stream().map(FlowStepDTO::getApprovers).toList();
  }
}
