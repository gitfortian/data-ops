package io.yak.ops.business.asset.application;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 跨域引用摘要(docs/PLATFORM_CORE_FLOW.md §四,M2-3):"上游 N 张表,其中 M 张未稽核"。
 *
 * <p>分叉 1 的口径——消费侧(指标详情等)不自建状态格,只引用既有事实:血缘给上游表集合,
 * 质量给稽核结论。任一事实源缺失或查询异常一律 {@code available=false}(计数按 -1 输出),
 * 与状态条同一诚实纪律,不伪造。
 *
 * <p>"未稽核"与状态条第 4 格同一判据:定位不了四元组、未挂监控、最近执行非 PASSED 都算。
 * 表按 (ds,db,schema,小写表名) 去重——与质量监控唯一键同口径,同一张表被多条边带回来不重复计数。
 */
@Service
@RequiredArgsConstructor
public class AssetLineageSummaryService {

  /**
   * @param available false 时结论不可信,原因见 reason(unauditedTables 为 -1;血缘不可用时两个计数都是 -1)
   * @param upstreamTables 去重后的上游 TABLE 节点数(不含根自身)
   * @param unauditedTables 其中未稽核的张数
   */
  public record LineageSummary(
      boolean available, String reason, int upstreamTables, int unauditedTables, int depth) {

    static LineageSummary unavailable(String reason) {
      return new LineageSummary(false, reason, -1, -1, 0);
    }
  }

  private final ObjectProvider<LineageQueryService> lineageQuery;
  private final ObjectProvider<QualityMonitorReader> qualityReader;

  public LineageSummary summarize(String assetKey, int depth) {
    LineageQueryService lineage = lineageQuery.getIfAvailable();
    if (lineage == null) {
      return LineageSummary.unavailable("血缘服务未装配");
    }
    List<LineageAsset> tables;
    try {
      LineageAsset root = lineage.getAssetByKey(assetKey);
      if (root == null) {
        return LineageSummary.unavailable("血缘域暂无该资产登记");
      }
      tables = dedup(lineage.graph(root.id(), LineageDirection.UPSTREAM, depth).nodes(), root);
    } catch (RuntimeException e) {
      return LineageSummary.unavailable("血缘查询失败: " + e.getMessage());
    }
    QualityMonitorReader quality = qualityReader.getIfAvailable();
    if (quality == null) {
      return new LineageSummary(false, "质量域未装配", tables.size(), -1, depth);
    }
    // 一个 (ds,db,schema) 作用域一次拉回该库全部监控再逐表匹配,而不是每表查一遍
    Map<String, Optional<List<TableMonitorSummary>>> scopeFacts = new HashMap<>();
    int unaudited = 0;
    for (LineageAsset table : tables) {
      if (!audited(quality, scopeFacts, table)) {
        unaudited++;
      }
    }
    return new LineageSummary(true, null, tables.size(), unaudited, depth);
  }

  /** 定位不了四元组、该作用域质量查询失败、未挂监控、最近执行非 PASSED —— 一律按未稽核计。 */
  private static boolean audited(
      QualityMonitorReader quality,
      Map<String, Optional<List<TableMonitorSummary>>> scopeFacts,
      LineageAsset table) {
    Long dsId = parsePositiveLong(table.dataSourceId());
    if (dsId == null || !StringUtils.hasText(table.databaseName())
        || !StringUtils.hasText(table.tableName())) {
      return false;
    }
    String scope = table.dataSourceId() + "\u0000" + table.databaseName() + "\u0000" + table.schemaName();
    Optional<List<TableMonitorSummary>> summaries =
        scopeFacts.computeIfAbsent(scope, unused -> {
          try {
            return Optional.of(quality.tableSummaries(
                dsId, table.databaseName(), nullToEmpty(table.schemaName())));
          } catch (RuntimeException e) {
            return Optional.empty();
          }
        });
    if (summaries.isEmpty()) {
      return false;
    }
    return summaries.get().stream()
        .anyMatch(s -> s.lastResult() == CheckResult.PASSED
            && table.tableName().equalsIgnoreCase(s.tableName()));
  }

  /** 去重 + 剔除根;key 与质量监控四元组同口径。 */
  private static List<LineageAsset> dedup(List<LineageAsset> nodes, LineageAsset root) {
    Map<String, LineageAsset> byKey = new LinkedHashMap<>();
    for (LineageAsset node : nodes) {
      if (node.assetType() != LineageAssetType.TABLE || node.id() == root.id()) {
        continue;
      }
      byKey.putIfAbsent(
          nullToEmpty(node.dataSourceId()) + "\u0000" + nullToEmpty(node.databaseName())
              + "\u0000" + nullToEmpty(node.schemaName()) + "\u0000"
              + String.valueOf(node.tableName()).toLowerCase(),
          node);
    }
    return new ArrayList<>(byKey.values());
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static Long parsePositiveLong(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      long value = Long.parseLong(raw.trim());
      return value > 0 ? value : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
