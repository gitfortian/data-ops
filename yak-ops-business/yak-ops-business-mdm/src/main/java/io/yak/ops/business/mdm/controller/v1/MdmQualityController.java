package io.yak.ops.business.mdm.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.mdm.application.MdmLineageService;
import io.yak.ops.business.mdm.application.MdmLineageService.LineageSyncReceipt;
import io.yak.ops.business.mdm.application.MdmQualityService;
import io.yak.ops.business.mdm.application.MdmQualityService.LandingCheckReceipt;
import io.yak.ops.business.mdm.application.MdmQualityService.LandingQualityStatus;
import io.yak.ops.common.constant.mdm.MdmPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主数据质量/血缘复用接口(R3):质量状态与一键体检转调 quality 模块服务,
 * 血缘三段同步转调 lineage 模块;MDM 零执行引擎,结果反查全部实时(D-M11).
 */
@Tag(name = "主数据质量与血缘接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mdm/entities/{entityId}")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MdmPermissionCode.READ)
public class MdmQualityController {

  private final MdmQualityService qualityService;
  private final MdmLineageService lineageService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "落地表质量状态(资产/监控/最近检查结果实时反查)")
  @GetMapping("/quality/status")
  public Result<List<LandingQualityStatus>> qualityStatus(
      @PathVariable("entityId") Long entityId) {
    return Result.success(qualityService.status(entityId));
  }

  @Operation(summary = "一键质量体检(注册资产+按模板建监控+执行,幂等可重复点击)")
  @RequiresPermission(MdmPermissionCode.CREATE)
  @PostMapping("/quality/check")
  public Result<List<LandingCheckReceipt>> qualityCheck(
      @PathVariable("entityId") Long entityId, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(qualityService.check(entityId, operator));
  }

  @Operation(summary = "同步三段血缘(源表→落地表→yak_mdm_record)")
  @RequiresPermission(MdmPermissionCode.CREATE)
  @PostMapping("/lineage/sync")
  public Result<LineageSyncReceipt> syncLineage(@PathVariable("entityId") Long entityId) {
    return Result.success(lineageService.sync(entityId));
  }
}
