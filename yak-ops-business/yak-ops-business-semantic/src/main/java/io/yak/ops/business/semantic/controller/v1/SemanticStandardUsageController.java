package io.yak.ops.business.semantic.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.semantic.api.StandardUsageApi;
import io.yak.ops.business.semantic.catalog.StandardCatalogService;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Usage summary on the standard detail page (ticket 42). */
@Tag(name = "业务语义标准使用统计接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/semantic/standards")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SemanticPermissionCode.READ)
public class SemanticStandardUsageController {

  private final StandardUsageApi usageApi;
  private final StandardCatalogService catalogService;

  public record UsageSummaryView(
      Long standardId, long applyCount, long bypassCount, boolean suspicious, String advice) {}

  @Operation(summary = "标准引用/绕过统计（服务端聚合；含反哺建议）")
  @GetMapping("/{id}/usage")
  public Result<UsageSummaryView> usage(@PathVariable("id") Long id) {
    catalogService.get(id);
    StandardUsageApi.UsageSummary summary = usageApi.summary(id);
    String advice =
        summary.suspicious()
            ? "绕过次数偏高，标准可能存在问题；建议复核（修改或废弃，由管理员决策）"
            : null;
    return Result.success(
        new UsageSummaryView(
            summary.standardId(), summary.applyCount(), summary.bypassCount(),
            summary.suspicious(), advice));
  }
}
