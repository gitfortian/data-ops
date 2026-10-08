package io.yak.ops.business.consumption.relationship;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据消费影响")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/consumption/impact")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(AssetPermissionCode.READ)
public class ConsumerImpactController {

  private final ConsumerImpactService service;

  @Operation(summary = "逐页恢复精确 DatasetVersion 保留成功审计的 Usage 证据")
  @PostMapping("/dataset-version-recovery")
  public Result<DatasetAuditRecoveryView> recoverDatasetVersionPage(
      @RequestParam String productKey,
      @RequestParam String sourceVersionIdentity,
      @RequestParam(required = false) Long beforeAuditId,
      @RequestParam(defaultValue = "200") int limit) {
    return Result.success(service.recoverDatasetVersionPage(
        ProductKey.parse(productKey), sourceVersionIdentity, beforeAuditId, limit));
  }

  @Operation(summary = "逐页恢复精确 Data Service Revision 保留成功调用审计的 Usage 证据")
  @PostMapping("/data-service-revision-recovery")
  public Result<DataServiceAuditRecoveryView> recoverDataServiceRevisionPage(
      @RequestParam String productKey,
      @RequestParam String sourceVersionIdentity,
      @RequestParam(required = false) String beforeInvocationId,
      @RequestParam(defaultValue = "200") int limit) {
    return Result.success(service.recoverDataServiceRevisionPage(
        ProductKey.parse(productKey), sourceVersionIdentity, beforeInvocationId, limit));
  }

  @Operation(summary = "查询已知 Consumer 与声明/实际消费证据")
  @GetMapping
  public Result<ConsumerImpactView> view(
      @RequestParam String productKey,
      @RequestParam(defaultValue = "200") int usageLimit,
      @RequestParam(required = false) String sourceVersionIdentity) {
    return Result.success(service.view(
        ProductKey.parse(productKey), usageLimit, sourceVersionIdentity));
  }
}
