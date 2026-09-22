package io.yak.ops.boot.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.constant.SecurityPermissionCode;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.audit.AuditFilterOptions;
import io.yak.ops.business.audit.AuditOperationDetail;
import io.yak.ops.business.audit.AuditOperationQuery;
import io.yak.ops.business.audit.AuditOperationSummary;
import io.yak.ops.business.audit.AuditPage;
import io.yak.ops.business.audit.AuditQueryService;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrator-facing read API for end-to-end business audit operations. */
@Tag(name = "Yak Ops 审计中心")
@RestController
@RequestMapping("/api/v1/audit")
@RequiresPermission(SecurityPermissionCode.OperationLog.READ)
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuditQueryController {

  private final AuditQueryService auditQueryService;

  public AuditQueryController(AuditQueryService auditQueryService) {
    this.auditQueryService = auditQueryService;
  }

  @Operation(summary = "分页查询审计操作")
  @PostMapping("/operations/page")
  public Result<PagingData<AuditOperationSummary>> page(
      @RequestBody(required = false) AuditOperationQuery query) {
    AuditPage<AuditOperationSummary> page =
        auditQueryService.page(query == null ? AuditOperationQuery.empty() : query);
    PageData<AuditOperationSummary> pageData =
        PageData.of(page.records(), page.total(), page.page(), page.size());
    return Result.success(PagingData.from(pageData));
  }

  @Operation(summary = "查询审计操作详情和时间线")
  @GetMapping("/operations/{operationId}")
  public Result<AuditOperationDetail> detail(@PathVariable String operationId) {
    Optional<AuditOperationDetail> detail = auditQueryService.detail(operationId);
    if (detail.isEmpty()) {
      return Result.buildNotExist("审计操作不存在");
    }
    return Result.success(detail.get());
  }

  @Operation(summary = "查询审计中心筛选选项")
  @GetMapping("/options")
  public Result<AuditFilterOptions> options() {
    return Result.success(auditQueryService.options());
  }
}
