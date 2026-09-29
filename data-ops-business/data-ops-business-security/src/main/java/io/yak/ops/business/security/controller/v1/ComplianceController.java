package io.yak.ops.business.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.security.application.ComplianceService;
import io.yak.ops.business.security.domain.ComplianceRunResult;
import io.yak.ops.common.bean.po.security.DsecComplianceFindingPO;
import io.yak.ops.common.bean.po.security.DsecComplianceRulePO;
import io.yak.ops.common.constant.security.SecurityPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 合规规则与体检(票据 78)。 */
@Tag(name = "数据安全-合规接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-security/compliance")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SecurityPermissionCode.READ)
public class ComplianceController {

  private final ComplianceService service;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "合规规则分页查询")
  @PostMapping("/rules/page")
  public Result<PagingData<DsecComplianceRulePO>> pageRules(@Valid @RequestBody PageQuery query) {
    return Result.success(
        PagingData.from(service.pageRules(query.pageNo(), query.pageSize(), query.keyword())));
  }

  @Operation(summary = "合规规则详情")
  @GetMapping("/rules/{id}")
  public Result<DsecComplianceRulePO> getRule(@PathVariable("id") Long id) {
    return Result.success(service.getRule(id));
  }

  @Operation(summary = "创建合规规则")
  @RequiresPermission(SecurityPermissionCode.CREATE)
  @PostMapping("/rules")
  public Result<DsecComplianceRulePO> createRule(@Valid @RequestBody RuleCreateRequest body,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(service.createRule(body.code(), body.name(), body.ruleType(),
        body.params(), body.severity(), body.enabled(), body.description(), operator));
  }

  @Operation(summary = "删除合规规则")
  @RequiresPermission(SecurityPermissionCode.DELETE)
  @DeleteMapping("/rules/{id}")
  public Result<Boolean> deleteRule(@PathVariable("id") Long id) {
    service.deleteRule(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "执行合规体检(规则级),返回批结果")
  @RequiresPermission(SecurityPermissionCode.CREATE)
  @PostMapping("/rules/{id}/run")
  public Result<ComplianceRunResult> run(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(service.run(id, operator));
  }

  @Operation(summary = "合规发现明细分页")
  @PostMapping("/findings/page")
  public Result<PagingData<DsecComplianceFindingPO>> pageFindings(
      @Valid @RequestBody FindingQuery query) {
    return Result.success(PagingData.from(service.pageFindings(query.pageNo(), query.pageSize(),
        query.batchId(), query.passed())));
  }

  @Operation(summary = "最近一次体检汇总")
  @GetMapping("/summary")
  public Result<Map<String, Object>> summary() {
    return Result.success(service.latestSummary());
  }

  public record PageQuery(int pageNo, int pageSize, String keyword) {}

  public record RuleCreateRequest(@NotBlank String code, @NotBlank String name,
      @NotBlank String ruleType, String params, String severity, Boolean enabled,
      String description) {}

  public record FindingQuery(int pageNo, int pageSize, String batchId, Boolean passed) {}
}
