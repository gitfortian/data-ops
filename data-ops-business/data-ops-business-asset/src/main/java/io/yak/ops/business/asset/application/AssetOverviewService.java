package io.yak.ops.business.asset.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.asset.dao.mapper.AssetChangeRecordMapper;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.dao.model.AssetChangeRecordPO;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetEnums.HandleStatus;
import io.yak.ops.common.enums.asset.AssetStatus;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 治理驾驶舱(设计 §3.9 / GET /overview):KPI + 分布 + 待办 + 最近动态。
 * 固定 8 次查询预算(4 组 group by + 1 聚合 + 1 变更计数 + 2 最近动态),全部服务端聚合。
 */
@Service
@RequiredArgsConstructor
public class AssetOverviewService {

  static final int RECENT_LIMIT = 5;
  static final int ADDED_WINDOW_DAYS = 30;

  private static final DateTimeFormatter SQL_TS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final CurrentProject currentProject;
  private final AssetItemMapper itemMapper;
  private final AssetChangeRecordMapper changeMapper;

  public Map<String, Object> overview() {
    Long projectId = currentProject.requireProjectId();

    List<Map<String, Object>> byStatus = groupCount(projectId, "status");
    List<Map<String, Object>> byGrade =
        groupCount(projectId, "COALESCE(health_grade, 'NONE')");
    List<Map<String, Object>> byType = groupCount(projectId, "asset_type");
    List<Map<String, Object>> byLayer =
        groupCount(projectId, "COALESCE(layer_code, 'UNSET')");

    // 查询 5:单条聚合行补齐 KPI/待办需要的计数(空表时 SUM 为 null)
    List<Map<String, Object>> aggRows = itemMapper.selectMaps(
        new QueryWrapper<AssetItemPO>()
            .select("COUNT(*) AS total",
                "SUM(CASE WHEN owner IS NULL OR owner = '' THEN 1 ELSE 0 END) AS no_owner",
                "SUM(CASE WHEN create_time >= '"
                    + LocalDateTime.now().minusDays(ADDED_WINDOW_DAYS).format(SQL_TS)
                    + "' THEN 1 ELSE 0 END) AS added_30d",
                "SUM(CASE WHEN security_level_code IS NULL OR security_level_code = ''"
                    + " THEN 1 ELSE 0 END) AS unclassified")
            .eq("project_id", projectId)
            .eq("deleted", 0));
    Map<String, Object> agg = aggRows == null || aggRows.isEmpty() || aggRows.get(0) == null
        ? Map.of() : aggRows.get(0);
    long total = num(agg.get("total"));

    // 查询 6:OPEN 变更待确认数
    long openChanges = changeMapper.selectCount(new LambdaQueryWrapper<AssetChangeRecordPO>()
        .eq(AssetChangeRecordPO::getProjectId, projectId)
        .eq(AssetChangeRecordPO::getDeleted, false)
        .eq(AssetChangeRecordPO::getHandleStatus, HandleStatus.OPEN.name()));

    Map<String, Long> statusCount = toCountMap(byStatus);
    Map<String, Long> gradeCount = toCountMap(byGrade);
    long published = statusCount.getOrDefault(AssetStatus.PUBLISHED.name(), 0L);
    long pending = statusCount.getOrDefault(AssetStatus.PENDING.name(), 0L);
    long sourceGone = statusCount.getOrDefault(AssetStatus.SOURCE_GONE.name(), 0L);
    long noOwner = num(agg.get("no_owner"));
    long withOwner = total - noOwner;

    Map<String, Object> kpis = new LinkedHashMap<>();
    kpis.put("total", total);
    kpis.put("published", published);
    kpis.put("pending", pending);
    kpis.put("added30d", num(agg.get("added_30d")));
    kpis.put("ownerCoverage", rate(withOwner, total));
    kpis.put("classifiedRate", rate(total - num(agg.get("unclassified")), total));
    kpis.put("gradeACount", gradeCount.getOrDefault("A", 0L));
    kpis.put("gradeDCount", gradeCount.getOrDefault("D", 0L));

    Map<String, Object> todos = new LinkedHashMap<>();
    todos.put("pendingPublish", pending);
    todos.put("openChanges", openChanges);
    todos.put("noOwner", noOwner);
    todos.put("gradeD", gradeCount.getOrDefault("D", 0L));
    todos.put("sourceGone", sourceGone);

    Map<String, Object> distributions = new LinkedHashMap<>();
    distributions.put("status", byStatus);
    distributions.put("grade", byGrade);
    distributions.put("type", byType);
    distributions.put("layer", byLayer);

    Map<String, Object> view = new LinkedHashMap<>();
    view.put("kpis", kpis);
    view.put("distributions", distributions);
    view.put("todos", todos);
    // 查询 7/8:最近上架与下架动态(各 LIMIT 5)
    view.put("recentListed", recent(projectId, AssetStatus.PUBLISHED.name(), true));
    view.put("recentOffline", recent(projectId, AssetStatus.OFFLINE.name(), false));
    view.put("generatedAt", LocalDateTime.now());
    return view;
  }

  // ---------- internal ----------

  private List<Map<String, Object>> groupCount(Long projectId, String groupExpr) {
    return itemMapper.selectMaps(new QueryWrapper<AssetItemPO>()
        .select(groupExpr + " AS k, COUNT(*) AS c")
        .eq("project_id", projectId)
        .eq("deleted", 0)
        .groupBy(groupExpr)
        .orderByDesc("c"));
  }

  /** listed=true 按 last_listed_at 倒序,false 按 last_offline_at。 */
  private List<Map<String, Object>> recent(Long projectId, String status, boolean listed) {
    List<AssetItemPO> items = itemMapper.selectList(new LambdaQueryWrapper<AssetItemPO>()
        .select(AssetItemPO::getId, AssetItemPO::getName, AssetItemPO::getAssetType,
            AssetItemPO::getOwner, AssetItemPO::getStatus,
            AssetItemPO::getLastListedAt, AssetItemPO::getLastOfflineAt)
        .eq(AssetItemPO::getProjectId, projectId)
        .eq(AssetItemPO::getDeleted, false)
        .eq(AssetItemPO::getStatus, status)
        .isNotNull(listed ? AssetItemPO::getLastListedAt : AssetItemPO::getLastOfflineAt)
        .orderBy(true, false, listed ? AssetItemPO::getLastListedAt : AssetItemPO::getLastOfflineAt)
        .last("LIMIT " + RECENT_LIMIT));
    List<Map<String, Object>> rows = new ArrayList<>();
    for (AssetItemPO po : items) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("assetId", po.getId());
      row.put("name", po.getName());
      row.put("assetType", po.getAssetType());
      row.put("owner", po.getOwner());
      row.put("at", listed ? po.getLastListedAt() : po.getLastOfflineAt());
      rows.add(row);
    }
    return rows;
  }

  private static Map<String, Long> toCountMap(List<Map<String, Object>> rows) {
    Map<String, Long> result = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      Object k = row.get("k");
      result.put(k == null ? "NONE" : String.valueOf(k), num(row.get("c")));
    }
    return result;
  }

  private static long num(Object value) {
    return value instanceof Number n ? n.longValue() : 0L;
  }

  /** 0..1 比率;分母 0 记 0(不伪造)。 */
  private static double rate(long part, long total) {
    return total == 0 ? 0 : Math.round(part * 10000.0 / total) / 10000.0;
  }
}
