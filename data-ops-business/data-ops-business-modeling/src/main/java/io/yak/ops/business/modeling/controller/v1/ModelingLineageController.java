package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.modeling.lineage.ModelingLineageRegistrationService;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lineage registration and graph query for models (ticket 23 / 24). */
@Tag(name = "数据建模血缘接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/models/{modelId}/lineage")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingLineageController {

  private static final Logger log = LoggerFactory.getLogger(ModelingLineageController.class);

  private final ModelingLineageRegistrationService registrationService;
  private final LineageQueryService lineageQueryService;
  private final CurrentUserProvider currentUserProvider;

  public record RegisterView(Long tableAssetId, int columnCount) {}

  public record AssetView(
      String id,
      String assetKey,
      LineageAssetType assetType,
      String name,
      String sourceType,
      String sourceId,
      String parentAssetId,
      String dataSourceId,
      String databaseName,
      String schemaName,
      String tableName,
      String columnName,
      JsonNode properties,
      Instant createTime,
      Instant updateTime) {}

  public record RelationView(
      String id,
      String sourceAssetId,
      String targetAssetId,
      LineageRelationType relationType,
      String sourceType,
      String sourceId,
      String expression,
      BigDecimal confidence,
      String version,
      Instant observedAt,
      JsonNode properties,
      Instant createTime,
      Instant updateTime) {}

  public record GraphView(
      AssetView root,
      LineageDirection direction,
      int depth,
      List<AssetView> nodes,
      List<RelationView> relations) {}

  @Operation(summary = "登记模型血缘（表+字段资产；幂等；用户确认后调用）")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PostMapping("/register")
  public Result<RegisterView> register(
      @PathVariable("modelId") Long modelId, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    ModelingLineageRegistrationService.RegisterView view =
        registrationService.registerModel(modelId, operator);
    return Result.success(new RegisterView(view.tableAssetId(), view.columnCount()));
  }

  @Operation(summary = "查询模型在血缘图谱中的资产ID")
  @GetMapping("/asset-id")
  public Result<Long> assetId(@PathVariable("modelId") Long modelId) {
    LineageAsset asset = findAssetByKeySafe(assetKey(modelId));
    return Result.success(asset != null ? asset.id() : null);
  }

  @Operation(summary = "查询模型血缘图（跨模块多跳遍历；未登记时自动触发登记）")
  @GetMapping("/graph")
  public Result<GraphView> graph(
      @PathVariable("modelId") Long modelId,
      @RequestParam(value = "direction", defaultValue = "BOTH") LineageDirection direction,
      @RequestParam(value = "depth", defaultValue = "3")
          @Min(1) @Max(LineageQueryService.MAX_GRAPH_DEPTH) int depth,
      HttpServletRequest httpRequest) {
    String key = assetKey(modelId);
    LineageAsset asset = findAssetByKeySafe(key);
    if (asset == null) {
      // Auto-register so the graph is available on first query.
      String operator = currentUserProvider.getCurrentUser(httpRequest);
      registrationService.registerModel(modelId, operator);
      asset = findAssetByKeySafe(key);
    }
    if (asset == null) {
      return Result.success(null);
    }
    LineageGraph graph = lineageQueryService.graph(asset.id(), direction, depth);
    return Result.success(toGraphView(graph));
  }

  private static GraphView toGraphView(LineageGraph value) {
    List<AssetView> nodes = value.nodes().stream().map(ModelingLineageController::toAssetView).toList();
    List<RelationView> relations =
        value.relations().stream().map(ModelingLineageController::toRelationView).toList();
    return new GraphView(toAssetView(value.root()), value.direction(), value.depth(), nodes, relations);
  }

  private static AssetView toAssetView(LineageAsset value) {
    if (value == null) return null;
    return new AssetView(
        String.valueOf(value.id()), value.assetKey(), value.assetType(), value.name(),
        value.sourceType(), value.sourceId(),
        value.parentAssetId() == null ? null : String.valueOf(value.parentAssetId()),
        value.dataSourceId(), value.databaseName(), value.schemaName(), value.tableName(),
        value.columnName(), value.properties(), value.createTime(), value.updateTime());
  }

  private static RelationView toRelationView(LineageRelation value) {
    if (value == null) return null;
    return new RelationView(
        String.valueOf(value.id()), String.valueOf(value.sourceAssetId()),
        String.valueOf(value.targetAssetId()), value.relationType(), value.sourceType(),
        value.sourceId(), value.expression(), value.confidence(), value.version(),
        value.observedAt(), value.properties(), value.createTime(), value.updateTime());
  }

  private static String assetKey(Long modelId) {
    return ModelingLineageRegistrationService.modelAssetKey(modelId);
  }

  private LineageAsset findAssetByKeySafe(String key) {
    try {
      return lineageQueryService.getAssetByKey(key);
    } catch (IllegalArgumentException e) {
      return null;
    } catch (RuntimeException e) {
      log.debug("Lineage asset lookup failed for key={}: {}", key, e.getMessage());
      return null;
    }
  }
}
