package io.yak.ops.business.asset.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.asset.dao.mapper.AssetViewRecordMapper;
import io.yak.ops.business.asset.dao.model.AssetViewRecordPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 浏览流水(ticket 97):同用户同资产 5 分钟内存去重后落库;趋势聚合走 SQL,零跨域。 */
@Service
@RequiredArgsConstructor
public class AssetViewRecordService {

  static final int DEDUPE_WINDOW_MINUTES = 5;
  private static final int MAX_DEDUPE_ENTRIES = 50_000;

  private final CurrentProject currentProject;
  private final AssetViewRecordMapper viewMapper;

  private final ConcurrentHashMap<String, LocalDateTime> lastSeen = new ConcurrentHashMap<>();

  /** 上报浏览;窗口内重复返回 false(仍算浏览成功,只是不再计)。 */
  public boolean record(Long assetId, String viewer, String entry) {
    Long projectId = currentProject.requireProjectId();
    LocalDateTime now = LocalDateTime.now();
    String key = projectId + ":" + viewer + ":" + assetId;
    LocalDateTime last = lastSeen.get(key);
    if (last != null && last.plusMinutes(DEDUPE_WINDOW_MINUTES).isAfter(now)) {
      return false;
    }
    lastSeen.put(key, now);
    if (lastSeen.size() > MAX_DEDUPE_ENTRIES) {
      lastSeen.values().removeIf(seen -> seen.plusMinutes(DEDUPE_WINDOW_MINUTES).isBefore(now));
    }
    AssetViewRecordPO po = new AssetViewRecordPO();
    po.setProjectId(projectId);
    po.setAssetId(assetId);
    po.setViewer(viewer);
    po.setViewTime(now);
    po.setEntry(entry);
    viewMapper.insert(po);
    return true;
  }

  /** 近 N 天按日浏览计数(缺日不补零,前端按日期对齐)。 */
  public List<DailyView> trend(Long assetId, int days) {
    Long projectId = currentProject.requireProjectId();
    List<Map<String, Object>> rows = viewMapper.selectMaps(new QueryWrapper<AssetViewRecordPO>()
        .select("DATE(view_time) AS view_date", "COUNT(*) AS view_count")
        .eq("project_id", projectId)
        .eq("asset_id", assetId)
        .ge("view_time", LocalDateTime.now().minusDays(days))
        .groupBy("DATE(view_time)")
        .orderByAsc("DATE(view_time)"));
    List<DailyView> result = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      result.add(new DailyView(String.valueOf(row.get("view_date")),
          ((Number) row.get("view_count")).longValue()));
    }
    return result;
  }

  /** 近 N 天页面访问摘要；独立访问者按平台用户标识去重。 */
  public ActivitySummary summary(Long assetId, int days) {
    Long projectId = currentProject.requireProjectId();
    List<Map<String, Object>> rows = viewMapper.selectMaps(new QueryWrapper<AssetViewRecordPO>()
        .select("COUNT(*) AS view_count", "COUNT(DISTINCT viewer) AS viewer_count",
            "MAX(view_time) AS last_viewed_at")
        .eq("project_id", projectId)
        .eq("asset_id", assetId)
        .ge("view_time", LocalDateTime.now().minusDays(days)));
    if (rows.isEmpty()) {
      return new ActivitySummary(0L, 0L, null);
    }
    Map<String, Object> row = rows.get(0);
    return new ActivitySummary(
        numberValue(row.get("view_count")),
        numberValue(row.get("viewer_count")),
        dateTimeValue(row.get("last_viewed_at")));
  }

  private static long numberValue(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private static LocalDateTime dateTimeValue(Object value) {
    if (value instanceof LocalDateTime dateTime) {
      return dateTime;
    }
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toLocalDateTime();
    }
    return null;
  }

  public record DailyView(String date, long count) {}

  public record ActivitySummary(long viewCount, long distinctViewerCount, LocalDateTime lastViewedAt) {}
}
