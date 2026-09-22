package io.yak.ops.business.metric.lineage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.maintenance.LineageMaintenanceService;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.lineage.registration.LineageRegistrationService;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.common.bean.po.metric.MetricDependencyPO;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges metric module to the global lineage graph (T51 refactoring).
 *
 * <p>On metric create/update: registers a {@code METRIC} asset and
 * {@code CONSUMES} relations for MODEL dependencies (which already
 * exist as TABLE assets registered by the modeling module).
 *
 * <p>On metric delete: evidence-scoped cleanup removes all lineage
 * relations sourced from this metric, and conservatively removes
 * unreferenced assets.
 *
 * <p>CALIBER/UNIT dependencies are tracked in {@code yak_metric_dependency}
 * only (for impact analysis), as they are semantic-domain entities without
 * dedicated lineage assets.
 */
@Component
@Slf4j
public class MetricLineageRegistrationService {

  private static final String SOURCE_TYPE = "METRIC";
  private static final String OWNER_TYPE = "METRIC";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final LineageRegistrationService registrationService;
  private final LineageMaintenanceService maintenanceService;
  private final LineageQueryService queryService;

  public MetricLineageRegistrationService(
      LineageRegistrationService registrationService,
      LineageMaintenanceService maintenanceService,
      LineageQueryService queryService) {
    this.registrationService = registrationService;
    this.maintenanceService = maintenanceService;
    this.queryService = queryService;
  }

  public record RegisterResult(Long assetId, int relationCount) {}

  /**
   * Register or replace lineage for a metric. Called after create/update.
   * Uses evidence-scoped replacement: old relations from this evidence
   * scope are cleared before new ones are registered.
   */
  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      propagation = Propagation.REQUIRES_NEW,
      rollbackFor = Exception.class)
  public RegisterResult registerMetric(Metric metric, List<MetricDependencyPO> dependencies) {
    String sourceId = String.valueOf(metric.id());

    // 1. Evidence-scoped replacement: clear old relations from this metric
    maintenanceService.clearRelationsByEvidence(SOURCE_TYPE, sourceId);

    // 2. Register (upsert) the metric as a METRIC asset
    ObjectNode properties = metricProperties(metric);
    LineageAsset metricAsset = registrationService.registerAsset(
        new LineageRegistrationService.RegisterAssetCommand(
            metricAssetKey(metric.id()),
            LineageAssetType.METRIC,
            metric.metricName(),
            SOURCE_TYPE,
            sourceId,
            null,       // parentAssetId
            null,       // dataSourceId
            null,       // databaseName
            null,       // schemaName
            null,       // tableName
            null,       // columnName
            properties,
            null));     // sourceProjectId

    // 3. Register relations by dependency type
    //    MODEL: upstream table asset -> metric (CONSUMES)
    //    REF_METRIC/COMPOSITION: upstream metric -> metric (DERIVES_FROM)
    int relationCount = 0;
    if (dependencies != null) {
      for (MetricDependencyPO dep : dependencies) {
        try {
          Long sourceAssetId = resolveSourceAssetId(dep);
          if (sourceAssetId == null) {
            log.debug("Upstream asset not found in lineage graph: type={}, depId={}, metricId={}",
                dep.getDependencyType(), dep.getDependencyId(), metric.id());
            continue;
          }
          LineageRelationType relationType = "MODEL".equals(dep.getDependencyType())
              ? LineageRelationType.CONSUMES
              : LineageRelationType.DERIVES_FROM;
          registrationService.registerRelation(
              new LineageRegistrationService.RegisterRelationCommand(
                  sourceAssetId,
                  metricAsset.id(),
                  relationType,
                  SOURCE_TYPE,
                  sourceId,
                  null,     // expression
                  null,     // confidence
                  "v" + metric.version(),
                  Instant.now(),
                  null,     // properties
                  null));   // sourceProjectId
          relationCount++;
        } catch (RuntimeException e) {
          log.warn("Failed to register lineage relation for metric {} -> {} {}: {}",
              metric.id(), dep.getDependencyType(), dep.getDependencyId(), e.getMessage());
        }
      }
    }

    return new RegisterResult(metricAsset.id(), relationCount);
  }

  /** 依赖行 → 血缘上游资产 id;上游未注册到血缘图时返回 null。 */
  private Long resolveSourceAssetId(MetricDependencyPO dep) {
    String type = dep.getDependencyType();
    if (dep.getDependencyId() == null) {
      return null;
    }
    if ("MODEL".equals(type)) {
      LineageAsset modelAsset = findAssetByKeySafe("modeling:model:" + dep.getDependencyId());
      return modelAsset != null ? modelAsset.id() : null;
    }
    if ("REF_METRIC".equals(type) || "COMPOSITION".equals(type)) {
      LineageAsset upstream = findAssetByKeySafe(metricAssetKey(dep.getDependencyId()));
      return upstream != null ? upstream.id() : null;
    }
    // CALIBER/UNIT: tracked in yak_metric_dependency only (semantic entities
    // without dedicated lineage assets).
    return null;
  }

  /**
   * Remove lineage data for a deleted metric.
   * Uses evidence-scoped cleanup: removes relations and unreferenced assets.
   */
  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      propagation = Propagation.REQUIRES_NEW,
      rollbackFor = Exception.class)
  public void removeMetric(Long metricId) {
    String sourceId = String.valueOf(metricId);
    try {
      LineageMaintenanceService.CleanupScope scope =
          maintenanceService.beginReplacement(SOURCE_TYPE, sourceId, OWNER_TYPE, sourceId);
      maintenanceService.finishReplacement(scope);
    } catch (RuntimeException e) {
      log.warn("Failed to cleanup lineage for deleted metric {}: {}",
          metricId, e.getMessage());
    }
  }

  /**
   * Look up the lineage asset ID for a metric.
   * Returns null if the metric has not been registered in the lineage graph.
   */
  public Long findAssetIdForMetric(Long metricId) {
    LineageAsset asset = findAssetByKeySafe(metricAssetKey(metricId));
    return asset != null ? asset.id() : null;
  }

  private LineageAsset findAssetByKeySafe(String key) {
    try {
      return queryService.getAssetByKey(key);
    } catch (IllegalArgumentException e) {
      return null;
    } catch (RuntimeException e) {
      log.debug("Lineage asset lookup failed for key={}: {}", key, e.getMessage());
      return null;
    }
  }

  /** 指标血缘登记键(全仓唯一出处:asset 对账与血缘登记同源复用,禁止在别处复制字面量)。 */
  public static String metricAssetKey(Long metricId) {
    return "metric:" + metricId;
  }

  private static ObjectNode metricProperties(Metric metric) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("metricCode", metric.metricCode());
    if (metric.metricType() != null) node.put("metricType", metric.metricType().name());
    if (metric.statPeriod() != null) node.put("statPeriod", metric.statPeriod().name());
    if (metric.domainId() != null) node.put("domainId", metric.domainId());
    if (metric.caliberId() != null) node.put("caliberId", metric.caliberId());
    if (metric.modelId() != null) node.put("modelId", metric.modelId());
    if (metric.unitId() != null) node.put("unitId", metric.unitId());
    return node;
  }
}
