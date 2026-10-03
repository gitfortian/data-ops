package io.yak.ops.business.metric.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.business.metric.dao.model.MetricVersionPO;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 指标版本历史 REST API（T50）。 */
@Tag(name = "指标版本接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metrics")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetricPermissionCode.READ)
public class MetricVersionController {

  private final MetricVersionRepository versionRepository;

  public enum VersionViewType {
    HISTORICAL_SNAPSHOT
  }

  public record VersionView(
      Long id, int version, String snapshot, String changeDesc,
      String changedBy, LocalDateTime createTime,
      VersionViewType versionViewType, boolean editable) {
    public static VersionView from(MetricVersionPO po) {
      return new VersionView(po.getId(), po.getVersion(), po.getSnapshot(),
          po.getChangeDesc(), po.getChangedBy(), po.getCreateTime(),
          VersionViewType.HISTORICAL_SNAPSHOT, false);
    }
  }

  @Operation(summary = "查询指标版本历史(最新在前)")
  @GetMapping("/{id}/versions")
  public Result<List<VersionView>> listVersions(@PathVariable("id") Long id) {
    return Result.success(versionRepository.listByMetric(id).stream()
        .map(VersionView::from).toList());
  }

  @Operation(summary = "查询指标指定版本快照")
  @GetMapping("/{id}/versions/{version}")
  public Result<VersionView> getVersion(
      @PathVariable("id") Long id,
      @PathVariable("version") int version) {
    MetricVersionPO po = versionRepository.findByMetricAndVersion(id, version);
    if (po == null) {
      throw new MetricException(MetricErrorCode.NOT_FOUND,
          "版本 " + version + " 不存在");
    }
    return Result.success(VersionView.from(po));
  }
}
