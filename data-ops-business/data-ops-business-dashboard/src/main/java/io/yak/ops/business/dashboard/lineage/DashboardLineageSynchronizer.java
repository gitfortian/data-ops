package io.yak.ops.business.dashboard.lineage;

import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardWidgetSnapshot;
import io.yak.ops.business.dashboard.gateway.lineage.DashboardLineageGraphGateway;
import io.yak.ops.business.dashboard.gateway.lineage.DashboardLineageGraphGateway.Asset;
import io.yak.ops.business.dashboard.gateway.lineage.DashboardLineageGraphGateway.AssetSpec;
import io.yak.ops.business.dashboard.gateway.lineage.DashboardLineageGraphGateway.AssetType;
import io.yak.ops.business.dashboard.gateway.lineage.DashboardLineageGraphGateway.RelationSpec;
import io.yak.ops.business.dashboard.gateway.lineage.DashboardLineageGraphGateway.RelationType;
import io.yak.ops.business.dashboard.lineage.DashboardInlineLineageExtractor.InlineBinding;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Converges the derived lineage projection for the effective Dashboard snapshot. */
@Component
public class DashboardLineageSynchronizer {

  public static final String EVIDENCE_SOURCE_TYPE = "DASHBOARD_BINDING";

  private final DashboardLineageGraphGateway lineage;
  private final DashboardInlineLineageExtractor inlineExtractor;

  public DashboardLineageSynchronizer(
      DashboardLineageGraphGateway lineage,
      DashboardInlineLineageExtractor inlineExtractor) {
    this.lineage = lineage;
    this.inlineExtractor = inlineExtractor;
  }

  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      propagation = Propagation.REQUIRES_NEW)
  public void syncVersion(
      DashboardAsset dashboard,
      DashboardVersion version,
      List<DashboardWidgetSnapshot> widgets,
      boolean published) {
    if (dashboard == null || dashboard.id() <= 0L) return;

    String evidenceId = String.valueOf(dashboard.id());
    lineage.clearRelationsByEvidence(EVIDENCE_SOURCE_TYPE, evidenceId);
    Asset dashboardAsset = registerDashboardAsset(dashboard, version, widgets, published);
    if (version == null) return;

    List<DashboardWidgetSnapshot> source = widgets == null ? List.of() : widgets;
    Instant observedAt = version.createTime() == null ? Instant.now() : version.createTime();
    for (DashboardWidgetSnapshot widget : source) {
      if (widget == null || widget.widgetKey() == null || widget.widgetKey().isBlank()) continue;
      if (widget.analysisId() != null && widget.analysisId() > 0L) {
        Asset chart = resolveAnalysisChart(widget.analysisId(), widget.title());
        registerContainment(
            chart,
            dashboardAsset,
            dashboard,
            version,
            widget,
            published,
            "LINKED_ANALYSIS",
            observedAt);
        continue;
      }
      if (widget.inlineAnalysis() != null) {
        InlineBinding inline = inlineExtractor.extract(widget.inlineAnalysis());
        Asset chart = registerInlineChart(
            dashboardAsset, dashboard, version, widget, inline, published);
        registerContainment(
            chart,
            dashboardAsset,
            dashboard,
            version,
            widget,
            published,
            "INLINE_ANALYSIS",
            observedAt);
        registerInlineUpstream(chart, dashboard, version, widget, inline, published, observedAt);
      }
    }
  }

  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      propagation = Propagation.REQUIRES_NEW)
  public void clear(long dashboardId) {
    if (dashboardId <= 0L) return;
    lineage.clearRelationsByEvidence(EVIDENCE_SOURCE_TYPE, String.valueOf(dashboardId));
  }

  private Asset registerDashboardAsset(
      DashboardAsset dashboard,
      DashboardVersion version,
      List<DashboardWidgetSnapshot> widgets,
      boolean published) {
    Map<String, Object> properties = new LinkedHashMap<>();
    if (dashboard.currentVersionId() != null) {
      properties.put("currentVersionId", String.valueOf(dashboard.currentVersionId()));
      properties.put("currentVersionNo", dashboard.currentVersionNo());
    }
    if (dashboard.publishedVersionId() != null) {
      properties.put("publishedVersionId", String.valueOf(dashboard.publishedVersionId()));
      properties.put("publishedVersionNo", dashboard.publishedVersionNo());
    }
    if (version != null) {
      properties.put("effectiveVersionId", String.valueOf(version.id()));
      properties.put("effectiveVersionNo", version.versionNo());
    }
    properties.put("effectiveSnapshot", published ? "PUBLISHED" : "DRAFT");
    properties.put("widgetCount", widgets == null ? 0 : widgets.size());

    String name = version != null && version.name() != null && !version.name().isBlank()
        ? version.name()
        : dashboard.name();
    return lineage.registerAsset(new AssetSpec(
        dashboardAssetKey(dashboard.id()),
        AssetType.DASHBOARD,
        name,
        "DASHBOARD",
        String.valueOf(dashboard.id()),
        null,
        null,
        null,
        null,
        null,
        null,
        properties));
  }

  private Asset resolveAnalysisChart(long analysisId, String title) {
    String key = analysisChartAssetKey(analysisId);
    try {
      return lineage.requireAssetByKey(key);
    } catch (IllegalArgumentException missing) {
      Map<String, Object> properties = new LinkedHashMap<>();
      properties.put("analysisId", String.valueOf(analysisId));
      properties.put("lineageRegistration", "DASHBOARD_FALLBACK");
      properties.put("bindingMode", "REUSABLE_ANALYSIS");
      return lineage.registerAsset(new AssetSpec(
          key,
          AssetType.CHART,
          title == null || title.isBlank() ? "analysis:" + analysisId : title,
          "ANALYSIS",
          String.valueOf(analysisId),
          null,
          null,
          null,
          null,
          null,
          null,
          properties));
    }
  }

  private Asset registerInlineChart(
      Asset dashboardAsset,
      DashboardAsset dashboard,
      DashboardVersion version,
      DashboardWidgetSnapshot widget,
      InlineBinding inline,
      boolean published) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("dashboardId", String.valueOf(dashboard.id()));
    properties.put("dashboardVersionId", String.valueOf(version.id()));
    properties.put("dashboardVersionNo", version.versionNo());
    properties.put("widgetKey", widget.widgetKey());
    properties.put("bindingMode", "INLINE_ANALYSIS");
    properties.put("effectiveSnapshot", published ? "PUBLISHED" : "DRAFT");
    properties.put("lineageParseStatus", inline.parseStatus());
    if (inline.datasetId() != null) properties.put("datasetId", String.valueOf(inline.datasetId()));
    if (inline.chartType() != null) properties.put("chartType", inline.chartType());
    properties.put("referencedFieldCount", inline.fieldUsages().size());
    return lineage.registerAsset(new AssetSpec(
        inlineChartAssetKey(dashboard.id(), widget.widgetKey()),
        AssetType.CHART,
        widget.title() == null || widget.title().isBlank() ? widget.widgetKey() : widget.title(),
        "DASHBOARD_INLINE_ANALYSIS",
        dashboard.id() + ":" + widget.widgetKey(),
        dashboardAsset.id(),
        null,
        null,
        null,
        null,
        null,
        properties));
  }

  private void registerContainment(
      Asset chart,
      Asset dashboard,
      DashboardAsset dashboardSource,
      DashboardVersion version,
      DashboardWidgetSnapshot widget,
      boolean published,
      String bindingMode,
      Instant observedAt) {
    Map<String, Object> properties = versionProperties(dashboardSource, version, published);
    properties.put("lineageLevel", "CHART");
    properties.put("widgetKey", widget.widgetKey());
    properties.put("bindingMode", bindingMode);
    if (widget.title() != null) properties.put("widgetTitle", widget.title());
    if (widget.analysisId() != null) properties.put("analysisId", String.valueOf(widget.analysisId()));
    properties.put("gridX", widget.x());
    properties.put("gridY", widget.y());
    properties.put("gridW", widget.w());
    properties.put("gridH", widget.h());
    lineage.registerRelation(new RelationSpec(
        chart.id(),
        dashboard.id(),
        RelationType.CONTAINS,
        EVIDENCE_SOURCE_TYPE,
        String.valueOf(dashboardSource.id()),
        null,
        BigDecimal.ONE,
        relationVersion(version, widget.widgetKey(), "contains"),
        observedAt,
        properties));
  }

  private void registerInlineUpstream(
      Asset chart,
      DashboardAsset dashboard,
      DashboardVersion version,
      DashboardWidgetSnapshot widget,
      InlineBinding inline,
      boolean published,
      Instant observedAt) {
    if (inline.datasetId() == null || inline.datasetId() <= 0L) return;

    Asset dataset = resolveDatasetAsset(inline.datasetId());
    Map<String, Object> datasetProperties = versionProperties(dashboard, version, published);
    datasetProperties.put("lineageLevel", "DATASET");
    datasetProperties.put("widgetKey", widget.widgetKey());
    datasetProperties.put("bindingMode", "INLINE_ANALYSIS");
    datasetProperties.put("datasetId", String.valueOf(inline.datasetId()));
    lineage.registerRelation(new RelationSpec(
        dataset.id(),
        chart.id(),
        RelationType.CONSUMES,
        EVIDENCE_SOURCE_TYPE,
        String.valueOf(dashboard.id()),
        null,
        BigDecimal.ONE,
        relationVersion(version, widget.widgetKey(), "dataset"),
        observedAt,
        datasetProperties));

    int index = 0;
    for (Map.Entry<String, List<String>> entry : inline.fieldUsages().entrySet()) {
      index++;
      Asset field = resolveDatasetFieldAsset(dataset, inline.datasetId(), entry.getKey());
      Map<String, Object> properties = versionProperties(dashboard, version, published);
      properties.put("lineageLevel", "DATASET_FIELD");
      properties.put("widgetKey", widget.widgetKey());
      properties.put("bindingMode", "INLINE_ANALYSIS");
      properties.put("datasetId", String.valueOf(inline.datasetId()));
      properties.put("fieldId", entry.getKey());
      properties.put("usageRoles", entry.getValue());
      lineage.registerRelation(new RelationSpec(
          field.id(),
          chart.id(),
          RelationType.CONSUMES,
          EVIDENCE_SOURCE_TYPE,
          String.valueOf(dashboard.id()),
          null,
          BigDecimal.ONE,
          relationVersion(version, widget.widgetKey(), "field:" + index),
          observedAt,
          properties));
    }
  }

  private Asset resolveDatasetAsset(long datasetId) {
    String key = datasetAssetKey(datasetId);
    try {
      return lineage.requireAssetByKey(key);
    } catch (IllegalArgumentException missing) {
      Map<String, Object> properties = new LinkedHashMap<>();
      properties.put("datasetId", String.valueOf(datasetId));
      properties.put("lineageRegistration", "DASHBOARD_INLINE_FALLBACK");
      return lineage.registerAsset(new AssetSpec(
          key,
          AssetType.DATASET,
          key,
          "DATASET",
          String.valueOf(datasetId),
          null,
          null,
          null,
          null,
          null,
          null,
          properties));
    }
  }

  private Asset resolveDatasetFieldAsset(Asset dataset, long datasetId, String fieldId) {
    String key = datasetFieldAssetKey(datasetId, fieldId);
    try {
      return lineage.requireAssetByKey(key);
    } catch (IllegalArgumentException missing) {
      Map<String, Object> properties = new LinkedHashMap<>();
      properties.put("datasetId", String.valueOf(datasetId));
      properties.put("fieldId", fieldId);
      properties.put("lineageRegistration", "DASHBOARD_INLINE_FALLBACK");
      return lineage.registerAsset(new AssetSpec(
          key,
          AssetType.DATASET_FIELD,
          fieldId,
          "DATASET",
          datasetId + ":" + fieldId,
          dataset.id(),
          null,
          null,
          null,
          null,
          null,
          properties));
    }
  }

  private Map<String, Object> versionProperties(
      DashboardAsset dashboard,
      DashboardVersion version,
      boolean published) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("dashboardId", String.valueOf(dashboard.id()));
    properties.put("dashboardVersionId", String.valueOf(version.id()));
    properties.put("dashboardVersionNo", version.versionNo());
    properties.put("effectiveSnapshot", published ? "PUBLISHED" : "DRAFT");
    return properties;
  }

  /** DASHBOARD 资产键全仓唯一出处(asset 台账与血缘同源,D6)。 */
  public static String dashboardAssetKey(long dashboardId) {
    return "dashboard:" + dashboardId;
  }

  static String analysisChartAssetKey(long analysisId) {
    return "chart:analysis:" + analysisId;
  }

  static String inlineChartAssetKey(long dashboardId, String widgetKey) {
    return "chart:dashboard:" + dashboardId + ":widget:" + widgetKey;
  }

  static String datasetAssetKey(long datasetId) {
    return "dataset:" + datasetId;
  }

  static String datasetFieldAssetKey(long datasetId, String fieldId) {
    return "dataset-field:" + datasetId + ":" + fieldId;
  }

  private String relationVersion(DashboardVersion version, String widgetKey, String suffix) {
    String value = "dashboard-v" + version.versionNo() + ":" + widgetKey + ":" + suffix;
    return value.length() <= 128 ? value : value.substring(0, 128);
  }
}
