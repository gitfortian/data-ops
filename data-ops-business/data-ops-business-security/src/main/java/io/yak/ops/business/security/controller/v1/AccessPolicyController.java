package io.yak.ops.business.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.security.approval.AccessPolicyApprovalService;
import io.yak.ops.business.security.application.AccessPolicyService;
import io.yak.ops.common.bean.po.security.DsecAccessPolicyPO;
import io.yak.ops.common.constant.security.SecurityPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 访问策略申请与审批(票据 75)。 */
@Tag(name = "数据安全-访问策略接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-security/access-policies")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SecurityPermissionCode.READ)
public class AccessPolicyController {

  private final AccessPolicyService service;
  private final AccessPolicyApprovalService approvalService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "策略分页查询")
  @PostMapping("/page")
  public Result<PagingData<DsecAccessPolicyPO>> page(@Valid @RequestBody PageQuery query) {
    return Result.success(PagingData.from(
        service.page(query.pageNo(), query.pageSize(), query.keyword(), query.status())));
  }

  @Operation(summary = "策略详情")
  @GetMapping("/{id}")
  public Result<DsecAccessPolicyPO> get(@PathVariable("id") Long id) {
    return Result.success(service.get(id));
  }

  @Operation(summary = "新建策略申请(初始 PENDING)")
  @RequiresPermission(SecurityPermissionCode.CREATE)
  @PostMapping
  public Result<DsecAccessPolicyPO> create(@RequestBody DsecAccessPolicyPO draft,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(service.create(draft, operator));
  }

  @Operation(summary = "编辑策略")
  @RequiresPermission(SecurityPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<DsecAccessPolicyPO> update(@PathVariable("id") Long id,
      @RequestBody DsecAccessPolicyPO patch) {
    return Result.success(service.update(id, patch));
  }

  @Operation(summary = "提交权限申请审批(需已配置 ACCESS_GRANT 流程;批准后自动授权)")
  @RequiresPermission(SecurityPermissionCode.UPDATE)
  @PostMapping("/{id}/apply-approval")
  public Result<ApprovalInstanceView> submitApplyApproval(@PathVariable("id") Long id,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(approvalService.submit(id, operator));
  }

  @Operation(summary = "停用策略")
  @RequiresPermission(SecurityPermissionCode.UPDATE)
  @PostMapping("/{id}/disable")
  public Result<Boolean> disable(@PathVariable("id") Long id) {
    service.disable(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "删除策略")
  @RequiresPermission(SecurityPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }

  public record PageQuery(int pageNo, int pageSize, String keyword, String status) {}

}
