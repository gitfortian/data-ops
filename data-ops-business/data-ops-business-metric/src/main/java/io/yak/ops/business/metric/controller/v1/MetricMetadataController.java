package io.yak.ops.business.metric.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metric.api.MetricUsageApi;
import io.yak.ops.business.metric.catalog.MetricCatalogService;
import io.yak.ops.business.metric.controller.v1.vo.MetricVO;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.repository.MetricDependencyRepository;
import io.yak.ops.business.metric.dao.model.MetricDependencyPO;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Metric metadata query API (T54): external-facing metadata endpoints.
 * Reuses data-service auth/rate-limiting infrastructure where available.
 */
@Tag(name = "指标元数据API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metrics/metadata")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetricPermissionCode.READ)
public class MetricMetadataController {

  private final MetricCatalogService catalogService;
  private final MetricDependencyRepository dependencyRepository;
  private final MetricUsageApi usageApi;

  @Operation(summary = "按ID查询指标元数据")
  @GetMapping("/{id}")
  public Result<MetricVO> getMetadata(@PathVariable("id") Long id) {
    return Result.success(MetricVO.from(catalogService.get(id)));
  }

  @Operation(summary = "按条件查询指标列表(元数据)")
  @GetMapping
  public Result<io.yak.framework.common.PageData<Metric>> list(
      @RequestParam(value = "domainId", required = false) Long domainId,
      @RequestParam(value = "type", required = false) String metricType,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(value = "pageSize", defaultValue = "50") int pageSize) {
    pageSize = Math.min(pageSize, 200);
    return Result.success(catalogService.page(pageNo, pageSize, domainId, metricType, null, keyword, null, null));
  }

  @Operation(summary = "查询指标依赖(元数据)")
  @GetMapping("/{id}/dependencies")
  public Result<List<DependencyView>> dependencies(@PathVariable("id") Long id) {
    catalogService.get(id);
    return Result.success(dependencyRepository.listByMetric(id).stream()
        .map(DependencyView::from).toList());
  }

  @Operation(summary = "查询指标使用统计(元数据)")
  @GetMapping("/{id}/usage-summary")
  public Result<MetricUsageApi.UsageSummary> usageSummary(@PathVariable("id") Long id) {
    catalogService.get(id);
    return Result.success(usageApi.summary(id));
  }

  public record DependencyView(
      Long id, String dependencyType, Long dependencyId,
      String dependencyCode, Integer dependencyVersion) {
    public static DependencyView from(MetricDependencyPO po) {
      return new DependencyView(po.getId(), po.getDependencyType(),
          po.getDependencyId(), po.getDependencyCode(), po.getDependencyVersion());
    }
  }
}
