package io.yak.ops.business.consumption.relationship.source;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.consumption.relationship.UsageNormalizationResult;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据消费使用证据")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/consumption/usage-evidence/data-service")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(AssetPermissionCode.UPDATE)
public class DataServiceUsageEvidenceController {

  private final DataServiceUsageEvidenceSynchronizer synchronizer;

  @Operation(summary = "将 Data Service 调用审计同步为 Usage Evidence")
  @PostMapping("/synchronize")
  public Result<List<UsageNormalizationResult>> synchronize(
      @RequestParam Long apiId,
      @RequestParam(defaultValue = "200") int limit) {
    if (apiId == null || apiId <= 0L) {
      throw new IllegalArgumentException("apiId 必须大于 0");
    }
    return Result.success(synchronizer.synchronizeRecentByProduct(apiId, limit));
  }
}
