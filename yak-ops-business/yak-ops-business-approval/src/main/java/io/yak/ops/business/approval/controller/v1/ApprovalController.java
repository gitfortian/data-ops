package io.yak.ops.business.approval.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.service.RbacPermissionService;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.approval.application.ApprovalService;
import io.yak.ops.business.approval.application.ApprovalService.ApprovalDetailView;
import io.yak.ops.business.approval.application.ApprovalService.HandledView;
import io.yak.ops.business.approval.application.ApprovalService.TodoView;
import io.yak.ops.business.approval.controller.v1.dto.ApprovalRequests.ApproveDTO;
import io.yak.ops.business.approval.controller.v1.dto.ApprovalRequests.CancelDTO;
import io.yak.ops.business.approval.controller.v1.dto.ApprovalRequests.MineQueryDTO;
import io.yak.ops.business.approval.controller.v1.dto.ApprovalRequests.PageQueryDTO;
import io.yak.ops.business.approval.controller.v1.dto.ApprovalRequests.RejectDTO;
import io.yak.ops.common.constant.approval.ApprovalPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 审批单 REST API(ticket 103):待办/我发起/我审批过/详情/通过/拒绝/撤销/按业务查。 */
@Tag(name = "审批中心-审批单")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/approvals")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ApprovalPermissionCode.READ)
public class ApprovalController {

  private final ApprovalService approvalService;
  private final CurrentUserProvider currentUserProvider;
  private final ObjectProvider<RbacPermissionService> rbacPermissionService;

  @Operation(summary = "我的待办(分页,倒序)")
  @GetMapping("/todo")
  public Result<PagingData<TodoView>> todo(
      @Valid PageQueryDTO query, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    PageData<TodoView> page = approvalService.todo(
        operator, query.getPageNo(), query.getPageSize());
    return Result.success(PagingData.from(page));
  }

  @Operation(summary = "待办角标 COUNT")
  @GetMapping("/todo/count")
  public Result<Long> todoCount(HttpServletRequest httpRequest) {
    return Result.success(
        approvalService.todoCount(currentUserProvider.getCurrentUser(httpRequest)));
  }

  @Operation(summary = "我发起的(status 可选过滤)")
  @GetMapping("/mine")
  public Result<PagingData<ApprovalInstanceView>> mine(
      @Valid MineQueryDTO query, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    PageData<ApprovalInstanceView> page = approvalService.mine(
        operator, query.getStatus(), query.getPageNo(), query.getPageSize());
    return Result.success(PagingData.from(page));
  }

  @Operation(summary = "我审批过的")
  @GetMapping("/handled")
  public Result<PagingData<HandledView>> handled(
      @Valid PageQueryDTO query, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    PageData<HandledView> page = approvalService.handled(
        operator, query.getPageNo(), query.getPageSize());
    return Result.success(PagingData.from(page));
  }

  @Operation(summary = "详情:单+payload+step 流水(仅当事人或 manage)")
  @GetMapping("/{id}")
  public Result<ApprovalDetailView> detail(
      @PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    boolean manage = rbacPermissionService.stream()
        .anyMatch(s -> s.hasPermission(operator, ApprovalPermissionCode.MANAGE));
    return Result.success(approvalService.detail(id, operator, manage));
  }

  @Operation(summary = "通过(comment 可选)")
  @RequiresPermission(ApprovalPermissionCode.APPROVE)
  @PostMapping("/{id}/approve")
  public Result<ApprovalInstanceView> approve(
      @PathVariable("id") Long id,
      @RequestBody(required = false) ApproveDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(approvalService.approve(
        id, dto == null ? null : dto.getComment(), operator));
  }

  @Operation(summary = "拒绝(comment 必填,服务层 49006)")
  @RequiresPermission(ApprovalPermissionCode.APPROVE)
  @PostMapping("/{id}/reject")
  public Result<ApprovalInstanceView> reject(
      @PathVariable("id") Long id,
      @RequestBody(required = false) RejectDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(approvalService.reject(
        id, dto == null ? null : dto.getComment(), operator));
  }

  @Operation(summary = "撤销(仅发起人)")
  @PostMapping("/{id}/cancel")
  public Result<Boolean> cancel(
      @PathVariable("id") Long id,
      @RequestBody(required = false) CancelDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    approvalService.cancel(id, operator, dto == null ? null : dto.getReason());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "按业务对象查审批(在途优先,否则最近一单;无则 data=null)")
  @GetMapping("/by-biz")
  public Result<ApprovalInstanceView> byBiz(
      @RequestParam("flowCode") String flowCode,
      @RequestParam("bizType") String bizType,
      @RequestParam("bizId") String bizId) {
    return Result.success(approvalService.find(flowCode, bizType, bizId));
  }
}
