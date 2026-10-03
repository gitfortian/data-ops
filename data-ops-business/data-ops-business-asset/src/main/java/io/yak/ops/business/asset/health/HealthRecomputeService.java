package io.yak.ops.business.asset.health;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.asset.dao.mapper.AssetChangeRecordMapper;
import io.yak.ops.business.asset.dao.mapper.AssetHealthSnapshotMapper;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.dao.mapper.AssetTagRelMapper;
import io.yak.ops.business.asset.dao.mapper.AssetViewRecordMapper;
import io.yak.ops.business.asset.health.HealthScorer.Availability;
import io.yak.ops.business.asset.health.HealthScorer.HealthInputs;
import io.yak.ops.business.asset.health.HealthScorer.HealthResult;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.security.api.SecurityClassificationQueryApi;
import io.yak.ops.business.asset.dao.model.AssetChangeRecordPO;
import io.yak.ops.business.asset.dao.model.AssetHealthSnapshotPO;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import io.yak.ops.business.asset.dao.model.AssetTagRelPO;
import io.yak.ops.business.asset.dao.model.AssetViewRecordPO;
import io.yak.ops.common.enums.asset.AssetEnums.HandleStatus;
import io.yak.ops.common.enums.asset.AssetStatus;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 健康度重算入口(ticket 98):每日全量(含浏览缓存聚合与流水 90 天清理)+
 * 上架/打标/确认变更时的单资产即时重算;评分口径全部在 {@link HealthScorer}。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HealthRecomputeService {

  static final int VIEW_RETENTION_DAYS = 90;
  static final int VIEW_WINDOW_DAYS = 30;
  private static final String LAYER_ALL = "ALL";

  private final AssetItemMapper itemMapper;
  private final AssetChangeRecordMapper changeMapper;
  private final AssetTagRelMapper tagRelMapper;
  private final AssetViewRecordMapper viewMapper;
  private final AssetHealthSnapshotMapper snapshotMapper;
  private final ObjectProvider<LineageQueryService> lineageQuery;
  private final ObjectProvider<SecurityClassificationQueryApi> securityQuery;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public record RecompileReport(int assets, int viewRowsWritten, int purgedViews) {}

  /** 每日任务:浏览数聚合缓存 → 全量评分 → 流水清理。 */
  public RecompileReport recomputeAll(Long projectId) {
    Map<Long, Integer> views = aggregateViews(projectId);
    int written = applyViewCache(projectId, views);
    List<AssetItemPO> items = itemMapper.selectList(new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, projectId)
        .eq(AssetItemPO::getDeleted, false));
    for (AssetItemPO po : items) {
      recomputePo(po, views.getOrDefault(po.getId(), 0));
    }
    int purged = viewMapper.delete(new LambdaQueryWrapper<AssetViewRecordPO>()
        .eq(AssetViewRecordPO::getProjectId, projectId)
        .lt(AssetViewRecordPO::getViewTime, LocalDateTime.now().minusDays(VIEW_RETENTION_DAYS)));
    return new RecompileReport(items.size(), written, purged);
  }

  /** 即时重算入口(上架/打标/确认变更后调用);单项失败只记日志不阻塞业务操作。 */
  public void recomputeItems(Long projectId, List<Long> assetIds) {
    Map<Long, Integer> views = aggregateViews(projectId);
    for (Long id : assetIds) {
      try {
        AssetItemPO po = itemMapper.selectOne(new LambdaQueryWrapper<AssetItemPO>()
            .eq(AssetItemPO::getProjectId, projectId)
            .eq(AssetItemPO::getId, id)
            .eq(AssetItemPO::getDeleted, false));
        if (po != null) {
          recomputePo(po, views.getOrDefault(id, 0));
        }
      } catch (RuntimeException e) {
        log.warn("asset health recompute failed, asset={}", id, e);
      }
    }
  }

  /** 每日 04:00 概览快照:分层×等级计数 upsert(P2 趋势)。 */
  public int snapshotDaily(Long projectId) {
    List<Map<String, Object>> rows = itemMapper.selectMaps(new QueryWrapper<AssetItemPO>()
        .select("COALESCE(layer_code, 'UNSET') AS layer",
            "COALESCE(health_grade, 'NONE') AS grade",
            "SUM(CASE WHEN status = '" + AssetStatus.PUBLISHED.name() + "' THEN 1 ELSE 0 END)"
                + " AS published",
            "COUNT(*) AS total")
        .eq("project_id", projectId)
        .eq("deleted", 0)
        .groupBy("COALESCE(layer_code, 'UNSET')", "COALESCE(health_grade, 'NONE')"));
    LocalDate today = LocalDate.now();
    Map<String, AssetHealthSnapshotPO> byLayer = new HashMap<>();
    for (Map<String, Object> row : rows) {
      String layer = String.valueOf(row.get("layer"));
      String grade = String.valueOf(row.get("grade"));
      int count = ((Number) row.get("total")).intValue();
      int published = ((Number) row.get("published")).intValue();
      accumulate(byLayer.computeIfAbsent(layer, k -> blank(projectId, today, k)), grade, count);
      accumulatePublished(byLayer.computeIfAbsent(layer, k -> blank(projectId, today, k)),
          published);
      if (!LAYER_ALL.equals(layer)) {
        AssetHealthSnapshotPO all =
            byLayer.computeIfAbsent(LAYER_ALL, k -> blank(projectId, today, k));
        accumulate(all, grade, count);
        accumulatePublished(all, published);
      }
    }
    for (AssetHealthSnapshotPO po : byLayer.values()) {
      AssetHealthSnapshotPO existing = snapshotMapper.selectOne(
          new LambdaQueryWrapper<AssetHealthSnapshotPO>()
              .eq(AssetHealthSnapshotPO::getProjectId, projectId)
              .eq(AssetHealthSnapshotPO::getSnapshotDate, today)
              .eq(AssetHealthSnapshotPO::getLayerCode, po.getLayerCode()));
      if (existing == null) {
        snapshotMapper.insert(po);
      } else {
        po.setId(existing.getId());
        snapshotMapper.updateById(po);
      }
    }
    return byLayer.size();
  }

  // ---------- gather & write ----------

  private void recomputePo(AssetItemPO po, int viewCount) {
    int tags = Math.toIntExact(tagRelMapper.selectCount(new LambdaQueryWrapper<AssetTagRelPO>()
        .eq(AssetTagRelPO::getProjectId, po.getProjectId())
        .eq(AssetTagRelPO::getAssetId, po.getId())));
    boolean openChanges = changeMapper.selectCount(new LambdaQueryWrapper<AssetChangeRecordPO>()
        .eq(AssetChangeRecordPO::getProjectId, po.getProjectId())
        .eq(AssetChangeRecordPO::getAssetId, po.getId())
        .eq(AssetChangeRecordPO::getHandleStatus, HandleStatus.OPEN.name())
        .eq(AssetChangeRecordPO::getDeleted, false)) > 0;
    HealthInputs inputs = gather(po, tags, openChanges, viewCount);
    HealthResult result = HealthScorer.score(inputs);
    po.setHealthScore(result.score());
    po.setHealthGrade(result.grade());
    po.setHealthDetail(writeJson(result));
    itemMapper.updateById(po);
  }

  private HealthInputs gather(AssetItemPO po, int tags, boolean openChanges, int viewCount) {
    boolean physical = "TABLE".equals(po.getAssetType());
    boolean fieldBearing = physical || "DATASET".equals(po.getAssetType());
    boolean manual = AssetSourceType.MANUAL.name().equals(po.getSourceType());

    // 血缘:一次键解析同时服务"是否登记"与"下游引用"。
    LineageProbe lineage = manual
        ? new LineageProbe(Availability.NOT_APPLICABLE, null)
        : probeLineage(po);
    Availability downstreamAvail;
    int downstreamCount = 0;
    if (!physical && !"DATASET".equals(po.getAssetType()) && !"METRIC".equals(po.getAssetType())) {
      downstreamAvail = Availability.NOT_APPLICABLE;
    } else if (lineage.root() == null) {
      downstreamAvail = lineage.availability();
    } else {
      try {
        downstreamCount = lineageQuery.getObject()
            .graph(lineage.root().id(), LineageDirection.DOWNSTREAM, 1).relations().size();
        downstreamAvail = Availability.OK;
      } catch (RuntimeException e) {
        downstreamAvail = Availability.UNAVAILABLE;
      }
    }

    SecurityProbe security = probeSecurity(po, physical);
    LocalDateTime updated = po.getSourceUpdatedAt() != null ? po.getSourceUpdatedAt()
        : po.getUpdateTime();
    return new HealthInputs(
        StringUtils.hasText(po.getDescription()),
        StringUtils.hasText(po.getOwner()),
        po.getDirectoryId() != null,
        tags,
        // 字段注释覆盖率与质量监控 SPI 均未就绪(缺口 G1):适用项按 0 分标"数据不可用"
        fieldBearing ? Availability.UNAVAILABLE : Availability.NOT_APPLICABLE, 0,
        physical ? Availability.UNAVAILABLE : Availability.NOT_APPLICABLE, 0,
        lineage.availability(), lineage.root() != null,
        security.availability(), security.classified(),
        openChanges,
        downstreamAvail, downstreamCount,
        updated == null ? Availability.UNAVAILABLE : Availability.OK,
        updated == null ? null : Duration.between(updated, LocalDateTime.now()).toDays(),
        viewCount);
  }

  private record LineageProbe(Availability availability, LineageAsset root) {}

  private LineageProbe probeLineage(AssetItemPO po) {
    LineageQueryService service = lineageQuery.getIfAvailable();
    if (service == null) {
      return new LineageProbe(Availability.UNAVAILABLE, null);
    }
    try {
      return new LineageProbe(Availability.OK, service.getAssetByKey(po.getAssetKey()));
    } catch (RuntimeException e) {
      return new LineageProbe(Availability.UNAVAILABLE, null);
    }
  }

  private record SecurityProbe(Availability availability, boolean classified) {}

  /** 定级只对物理表类适用;快照列优先,实时查不到按未定级(不伪造)。 */
  private SecurityProbe probeSecurity(AssetItemPO po, boolean physical) {
    if (!physical) {
      return new SecurityProbe(Availability.NOT_APPLICABLE, false);
    }
    if (StringUtils.hasText(po.getSecurityLevelCode())) {
      return new SecurityProbe(Availability.OK, true);
    }
    SecurityClassificationQueryApi api = securityQuery.getIfAvailable();
    if (api == null) {
      return new SecurityProbe(Availability.UNAVAILABLE, false);
    }
    try {
      return new SecurityProbe(Availability.OK, api.find(po.getAssetKey()) != null);
    } catch (RuntimeException e) {
      return new SecurityProbe(Availability.UNAVAILABLE, false);
    }
  }

  // ---------- views cache ----------

  private Map<Long, Integer> aggregateViews(Long projectId) {
    List<Map<String, Object>> rows = viewMapper.selectMaps(new QueryWrapper<AssetViewRecordPO>()
        .select("asset_id", "COUNT(*) AS cnt")
        .eq("project_id", projectId)
        .ge("view_time", LocalDateTime.now().minusDays(VIEW_WINDOW_DAYS))
        .groupBy("asset_id"));
    Map<Long, Integer> result = new HashMap<>();
    for (Map<String, Object> row : rows) {
      result.put(((Number) row.get("asset_id")).longValue(),
          ((Number) row.get("cnt")).intValue());
    }
    return result;
  }

  private int applyViewCache(Long projectId, Map<Long, Integer> views) {
    List<AssetItemPO> items = itemMapper.selectList(new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, projectId)
        .eq(AssetItemPO::getDeleted, false));
    int written = 0;
    for (AssetItemPO po : items) {
      int value = views.getOrDefault(po.getId(), 0);
      if (po.getViewCount30d() == null || po.getViewCount30d() != value) {
        AssetItemPO patch = new AssetItemPO();
        patch.setId(po.getId());
        patch.setViewCount30d(value);
        itemMapper.updateById(patch);
        po.setViewCount30d(value);
        written++;
      }
    }
    return written;
  }

  // ---------- snapshot helpers ----------

  private static AssetHealthSnapshotPO blank(Long projectId, LocalDate date, String layer) {
    AssetHealthSnapshotPO po = new AssetHealthSnapshotPO();
    po.setProjectId(projectId);
    po.setSnapshotDate(date);
    po.setLayerCode(layer);
    po.setGradeACount(0);
    po.setGradeBCount(0);
    po.setGradeCCount(0);
    po.setGradeDCount(0);
    po.setPublishedCount(0);
    return po;
  }

  private static void accumulate(AssetHealthSnapshotPO po, String grade, int count) {
    switch (grade) {
      case "A" -> po.setGradeACount(po.getGradeACount() + count);
      case "B" -> po.setGradeBCount(po.getGradeBCount() + count);
      case "C" -> po.setGradeCCount(po.getGradeCCount() + count);
      case "D" -> po.setGradeDCount(po.getGradeDCount() + count);
      default -> {
      }
    }
  }

  private static void accumulatePublished(AssetHealthSnapshotPO po, int published) {
    po.setPublishedCount(po.getPublishedCount() + published);
  }

  private static String writeJson(HealthResult result) {
    try {
      return MAPPER.writeValueAsString(new ArrayList<>(result.items()));
    } catch (Exception e) {
      return "[]";
    }
  }
}
