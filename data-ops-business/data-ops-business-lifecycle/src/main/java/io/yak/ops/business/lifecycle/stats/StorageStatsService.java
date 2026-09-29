package io.yak.ops.business.lifecycle.stats;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.lifecycle.dao.mapper.LifecycleDispatchRecordMapper;
import io.yak.ops.business.lifecycle.dao.mapper.LifecycleSettingMapper;
import io.yak.ops.business.lifecycle.dao.mapper.LifecycleStorageSnapshotMapper;
import io.yak.ops.common.bean.po.lifecycle.LifecycleDispatchRecordPO;
import io.yak.ops.common.bean.po.lifecycle.LifecycleSettingPO;
import io.yak.ops.common.bean.po.lifecycle.LifecycleStorageSnapshotPO;
import io.yak.ops.core.project.CurrentProject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 存储统计查询(ticket 87):各层量/热冷分布(按最近下发归类比例推算)/趋势/月度成本估算。 */
@Service
@RequiredArgsConstructor
public class StorageStatsService {

  private final io.yak.ops.core.project.CurrentProject currentProject;

  public static final String PRICE_KEY = "storage_price_gb_month";

  public record LayerVolume(String layerCode, long sizeBytes, double sizeGb) {}

  public record HotColdSplit(long hotBytes, long coldBytes, boolean estimated) {}

  public record TrendPoint(LocalDate date, long sizeBytes) {}

  public record StorageStats(
      LocalDate snapshotDate,
      long totalBytes,
      List<LayerVolume> byLayer,
      HotColdSplit hotCold,
      Double monthlyCost,
      String pricePerGbMonth) {}

  /** 单表快照。{@code sizeGb} 只为展示方便，比较与汇总一律用 {@code sizeBytes}。 */
  public record TableStorage(
      LocalDate snapshotDate,
      String layerCode,
      Long datasourceId,
      String databaseName,
      String tableName,
      long sizeBytes,
      double sizeGb) {}

  private final LifecycleStorageSnapshotMapper snapshotMapper;
  private final LifecycleDispatchRecordMapper dispatchRecordMapper;
  private final LifecycleSettingMapper settingMapper;

  public StorageStats stats() {
    Long projectId = currentProject.requireProjectId();
    List<LifecycleStorageSnapshotPO> latest = latestSnapshotRows(projectId);
    Map<String, Long> byLayer = new LinkedHashMap<>();
    long total = 0;
    LocalDate date = null;
    for (LifecycleStorageSnapshotPO row : latest) {
      date = date == null || row.getSnapshotDate().isAfter(date) ? row.getSnapshotDate() : date;
      String layer = row.getLayerCode() == null ? "UNKNOWN" : row.getLayerCode();
      byLayer.merge(layer, row.getSizeBytes() == null ? 0 : row.getSizeBytes(), Long::sum);
      total += row.getSizeBytes() == null ? 0 : row.getSizeBytes();
    }
    List<LayerVolume> volumes = byLayer.entrySet().stream()
        .map(e -> new LayerVolume(e.getKey(), e.getValue(), gb(e.getValue())))
        .sorted(Comparator.comparing(LayerVolume::layerCode))
        .toList();
    HotColdSplit split = hotColdSplit(projectId, latest);
    String price = getSetting(PRICE_KEY);
    Double cost = price == null ? null
        : round(gb(total) * Double.parseDouble(price), 2);
    return new StorageStats(date, total, volumes, split, cost, price);
  }

  public List<TrendPoint> trend(int days) {
    int window = Math.min(Math.max(days, 1), 365);
    List<LifecycleStorageSnapshotPO> rows = snapshotMapper.selectList(
        new LambdaQueryWrapper<LifecycleStorageSnapshotPO>()
            .eq(LifecycleStorageSnapshotPO::getProjectId, currentProject.requireProjectId())
            .ge(LifecycleStorageSnapshotPO::getSnapshotDate, LocalDate.now().minusDays(window)));
    Map<LocalDate, Long> totals = new java.util.TreeMap<>();
    rows.forEach(r -> totals.merge(r.getSnapshotDate(),
        r.getSizeBytes() == null ? 0 : r.getSizeBytes(), Long::sum));
    List<TrendPoint> points = new ArrayList<>();
    LocalDate cursor = LocalDate.now().minusDays(window - 1L);
    while (!cursor.isAfter(LocalDate.now())) {
      points.add(new TrendPoint(cursor, totals.getOrDefault(cursor, 0L)));
      cursor = cursor.plusDays(1);
    }
    return points;
  }

  /**
   * 一张表的最近快照（元数据详情页的存储块）。
   *
   * <p><b>必须带 {@code databaseName}</b>：快照表的唯一键不含库名（plan §9 T16），同数据源多库的
   * 同名表会互相覆盖，只按表名查会把别人的库报成这张表的大小。该缺陷归采集侧修（工单 134 之后另开），
   * 这里不偷偷补偿，只是不让读侧再犯一次。
   *
   * <p>返回空 = 这张表没有快照（分层配置未覆盖该库、或采集还没跑过），<b>不是</b> 0 字节。
   * 调用方要显示"近似"与快照日期：它是每日采一次的现场，不是实时值。
   */
  public Optional<TableStorage> latestTableStorage(
      Long datasourceId, String databaseName, String tableName) {
    if (!StringUtils.hasText(databaseName) || !StringUtils.hasText(tableName)) {
      return Optional.empty();
    }
    return snapshotMapper
        .selectList(
            new LambdaQueryWrapper<LifecycleStorageSnapshotPO>()
                .eq(LifecycleStorageSnapshotPO::getProjectId, currentProject.requireProjectId())
                .eq(datasourceId != null, LifecycleStorageSnapshotPO::getDatasourceId, datasourceId)
                .eq(LifecycleStorageSnapshotPO::getDatabaseName, databaseName)
                .eq(LifecycleStorageSnapshotPO::getTableName, tableName)
                .orderByDesc(LifecycleStorageSnapshotPO::getSnapshotDate)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(StorageStatsService::toTableStorage);
  }

  private static TableStorage toTableStorage(LifecycleStorageSnapshotPO po) {
    long bytes = po.getSizeBytes() == null ? 0 : po.getSizeBytes();
    return new TableStorage(
        po.getSnapshotDate(), po.getLayerCode(), po.getDatasourceId(),
        po.getDatabaseName(), po.getTableName(), bytes, gb(bytes));
  }

  public String getSetting(String key) {
    LifecycleSettingPO po = findSetting(key);
    return po == null ? null : po.getSettingValue();
  }

  public void putSetting(String key, String value) {
    Long projectId = currentProject.requireProjectId();
    LifecycleSettingPO po = findSetting(key);
    if (po == null) {
      po = new LifecycleSettingPO();
      po.setProjectId(projectId);
      po.setSettingKey(key);
      po.setSettingValue(value);
      po.setUpdateTime(LocalDateTime.now());
      settingMapper.insert(po);
    } else {
      po.setSettingValue(value);
      po.setUpdateTime(LocalDateTime.now());
      settingMapper.updateById(po);
    }
  }

  /** 热冷分布:DORIS 最近成功下发记录的分区归类占比推算(表大小不可按分区拆)。 */
  private HotColdSplit hotColdSplit(Long projectId, List<LifecycleStorageSnapshotPO> latest) {
    long total = latest.stream().mapToLong(r -> r.getSizeBytes() == null ? 0 : r.getSizeBytes()).sum();
    Map<Long, LifecycleDispatchRecordPO> latestSuccess = new LinkedHashMap<>();
    dispatchRecordMapper.selectList(new LambdaQueryWrapper<LifecycleDispatchRecordPO>()
            .eq(LifecycleDispatchRecordPO::getProjectId, projectId)
            .eq(LifecycleDispatchRecordPO::getStatus, "SUCCESS")
            .orderByAsc(LifecycleDispatchRecordPO::getId))
        .forEach(r -> latestSuccess.put(r.getModelId(), r));
    long hot = 0;
    long classified = 0;
    for (LifecycleDispatchRecordPO r : latestSuccess.values()) {
      int h = r.getPartitionHot() == null ? 0 : r.getPartitionHot();
      int c = r.getPartitionCold() == null ? 0 : r.getPartitionCold();
      if (h + c > 0) {
        hot += h;
        classified += h + (long) c;
      }
    }
    if (classified == 0) {
      return new HotColdSplit(0, total, true);
    }
    long hotBytes = Math.round(total * (double) hot / classified);
    return new HotColdSplit(hotBytes, total - hotBytes, true);
  }

  private List<LifecycleStorageSnapshotPO> latestSnapshotRows(Long projectId) {
    LocalDate maxDate = snapshotMapper.selectList(
            new LambdaQueryWrapper<LifecycleStorageSnapshotPO>()
                .eq(LifecycleStorageSnapshotPO::getProjectId, projectId)
                .orderByDesc(LifecycleStorageSnapshotPO::getSnapshotDate)
                .last("LIMIT 1"))
        .stream().findFirst().map(LifecycleStorageSnapshotPO::getSnapshotDate).orElse(null);
    if (maxDate == null) {
      return List.of();
    }
    return snapshotMapper.selectList(new LambdaQueryWrapper<LifecycleStorageSnapshotPO>()
        .eq(LifecycleStorageSnapshotPO::getProjectId, projectId)
        .eq(LifecycleStorageSnapshotPO::getSnapshotDate, maxDate));
  }

  private LifecycleSettingPO findSetting(String key) {
    return settingMapper.selectOne(new LambdaQueryWrapper<LifecycleSettingPO>()
        .eq(LifecycleSettingPO::getProjectId, currentProject.requireProjectId())
        .eq(LifecycleSettingPO::getSettingKey, key)
        .last("LIMIT 1"));
  }

  private static double gb(long bytes) {
    return round(bytes / (1024d * 1024 * 1024), 2);
  }

  private static double round(double v, int scale) {
    return BigDecimal.valueOf(v).setScale(scale, java.math.RoundingMode.HALF_UP).doubleValue();
  }
}
