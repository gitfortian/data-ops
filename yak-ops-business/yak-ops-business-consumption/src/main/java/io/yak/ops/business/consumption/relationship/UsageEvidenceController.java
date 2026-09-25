package io.yak.ops.business.consumption.relationship;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only product API for normalized successful consumption evidence. */
@Tag(name = "数据消费证据")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/consumption/usage-evidence")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(AssetPermissionCode.READ)
public class UsageEvidenceController {

  private final UsageEvidenceService service;
  private final CurrentProject currentProject;

  @Operation(summary = "查询项目内已归一化的成功消费证据")
  @GetMapping
  public Result<List<UsageEvidence>> list(
      @RequestParam String productKey,
      @RequestParam(required = false) ConsumerType consumerType,
      @RequestParam(required = false) String sourceDomain,
      @RequestParam(required = false) String sourceIdentity,
      @RequestParam(defaultValue = "50") int limit) {
    ConsumerRef consumerRef = consumerFilter(consumerType, sourceDomain, sourceIdentity);
    return Result.success(service.list(
        currentProject.requireProjectId(),
        ProductKey.parse(productKey),
        consumerRef,
        limit));
  }

  private static ConsumerRef consumerFilter(
      ConsumerType consumerType,
      String sourceDomain,
      String sourceIdentity) {
    boolean absent = consumerType == null
        && (sourceDomain == null || sourceDomain.isBlank())
        && (sourceIdentity == null || sourceIdentity.isBlank());
    if (absent) return null;
    if (consumerType == null || sourceDomain == null || sourceDomain.isBlank()
        || sourceIdentity == null || sourceIdentity.isBlank()) {
      throw new IllegalArgumentException(
          "consumerType, sourceDomain and sourceIdentity must be supplied together");
    }
    return new ConsumerRef(consumerType, sourceDomain, sourceIdentity, null);
  }
}
