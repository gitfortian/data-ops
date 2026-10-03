package io.yak.ops.business.metric.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.metric.lineage.MetricLineageRegistrationService;
import io.yak.ops.business.metric.repository.MetricDependencyRepository;
import io.yak.ops.business.metric.dao.model.MetricDependencyPO;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Metric lineage REST API (T51).
 *
 * <p>Exposes two views:
 * <ul>
 *   <li>{@code /{id}/lineage} — flat dependency list from {@code yak_metric_dependency}
 *       (includes CALIBER/UNIT that don't have lineage assets)</li>
 *   <li>{@code /{id}/graph} — multi-hop graph traversal from the global lineage graph
 *       ({@code yak_metadata_asset/relation}, cross-module)</li>
 * </ul>
 */
@Tag(name = "指标血缘接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metrics")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetricPermissionCode.READ)
public class MetricLineageController {

  private final MetricDependencyRepository dependencyRepository;
  private final MetricLineageRegistrationService lineageRegistrationService;
  private final LineageQueryService lineageQueryService;

  public record DependencyView(
      Long id, String dependencyType, Long dependencyId,
      String dependencyCode, Integer dependencyVersion,
      LocalDateTime createTime) {
    public static DependencyView from(MetricDependencyPO po) {
      return new DependencyView(po.getId(), po.getDependencyType(),
          po.getDependencyId(), po.getDependencyCode(),
          po.getDependencyVersion(), po.getCreateTime());
    }
  }

  public record AssetNodeView(
      long id, String assetKey, String assetType,
      String name, String sourceType) {
    public static AssetNodeView from(LineageAsset a) {
      return new AssetNodeView(a.id(), a.assetKey(),
          a.assetType() != null ? a.assetType().name() : null,
          a.name(), a.sourceType());
    }
  }

  public record RelationEdgeView(
      long id, long sourceAssetId, long targetAssetId,
      String relationType, String expression) {
    public static RelationEdgeView from(LineageRelation r) {
      return new RelationEdgeView(r.id(), r.sourceAssetId(), r.targetAssetId(),
          r.relationType() != null ? r.relationType().name() : null,
          r.expression());
    }
  }

  public record GraphView(
      AssetNodeView root, String direction, int depth,
      List<AssetNodeView> nodes, List<RelationEdgeView> relations) {
    public static GraphView from(LineageGraph graph) {
      return new GraphView(
          AssetNodeView.from(graph.root()),
          graph.direction().name(),
          graph.depth(),
          graph.nodes().stream().map(AssetNodeView::from).toList(),
          graph.relations().stream().map(RelationEdgeView::from).toList());
    }
  }

  @Operation(summary = "查询指标血缘登记(依赖列表,模块内)")
  @GetMapping("/{id}/lineage")
  public Result<List<DependencyView>> lineage(@PathVariable("id") Long id) {
    return Result.success(dependencyRepository.listByMetric(id).stream()
        .map(DependencyView::from).toList());
  }

  @Operation(summary = "查询指标血缘图(跨模块多跳遍历)")
  @GetMapping("/{id}/graph")
  public Result<GraphView> graph(
      @PathVariable("id") Long id,
      @RequestParam(value = "direction", defaultValue = "BOTH") LineageDirection direction,
      @RequestParam(value = "depth", defaultValue = "3")
          @Min(1) @Max(LineageQueryService.MAX_GRAPH_DEPTH) int depth) {
    Long assetId = lineageRegistrationService.findAssetIdForMetric(id);
    if (assetId == null) {
      return Result.success(null);
    }
    LineageGraph graph = lineageQueryService.graph(assetId, direction, depth);
    return Result.success(GraphView.from(graph));
  }

  @Operation(summary = "查询指标在血缘图谱中的资产ID")
  @GetMapping("/{id}/lineage/asset-id")
  public Result<Long> assetId(@PathVariable("id") Long id) {
    return Result.success(lineageRegistrationService.findAssetIdForMetric(id));
  }
}
