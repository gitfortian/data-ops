package io.yak.ops.business.lifecycle.stats;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.lifecycle.dao.mapper.LifecycleStorageSnapshotMapper;
import io.yak.ops.business.lifecycle.dispatch.TtlSqlGateway;
import io.yak.ops.business.semantic.api.LayerConfigApi;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.common.bean.po.lifecycle.LifecycleStorageSnapshotPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 每日存储快照采集(ticket 87):逐层库取表级字节量后 upsert 当日快照。
 * 分层配置不带方言信息,故先试 Doris 系 {@code SHOW DATA},语法不受支持时回落到标准
 * {@code information_schema.TABLES}(MySQL)。单库失败只记日志跳过,不阻断其余层(监控数据允许缺日)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageSnapshotService {

  private final io.yak.ops.core.project.CurrentProject currentProject;

  private static final Pattern SIZE = Pattern.compile(
      "([0-9]+(?:\\.[0-9]+)?)\\s*(TB|GB|MB|KB|B|bytes|字节)?", Pattern.CASE_INSENSITIVE);

  /** 可安全拼进 SQL 的库名字符集。 */
  private static final Pattern SAFE_DB = Pattern.compile("[A-Za-z0-9_]+");

  public record SnapshotResult(int layers, int tables, List<String> failedLayers) {}

  private final LifecycleStorageSnapshotMapper snapshotMapper;
  private final TtlSqlGateway gateway;
  private final LayerConfigApi layerConfigApi;

  public SnapshotResult collectDaily(String operator) {
    Long projectId = currentProject.requireProjectId();
    LocalDate today = LocalDate.now();
    int layers = 0;
    int tables = 0;
    List<String> failed = new java.util.ArrayList<>();
    List<WarehouseLayer> all = layerConfigApi.listLayers();
    if (all == null) {
      return new SnapshotResult(0, 0, List.of());
    }
    for (WarehouseLayer layer : all) {
      if (layer.datasourceId() == null || !StringUtils.hasText(layer.databaseName())) {
        continue;
      }
      try {
        tables += collectLayer(projectId, today, layer);
        layers++;
      } catch (RuntimeException e) {
        log.warn("storage snapshot failed for layer {}", layer.code(), e);
        failed.add(layer.code() + ": " + e.getMessage());
      }
    }
    return new SnapshotResult(layers, tables, failed);
  }

  private int collectLayer(Long projectId, LocalDate date, WarehouseLayer layer) {
    String db = layer.databaseName().replace("`", "").trim();
    List<List<Object>> rows = null;
    RuntimeException lastFailure = null;
    for (String sql : statementsFor(db)) {
      try {
        rows = gateway.query(layer.datasourceId(), sql, 5000);
        break;
      } catch (RuntimeException e) {
        lastFailure = e;
      }
    }
    if (rows == null) {
      throw lastFailure;
    }
    int count = 0;
    for (List<Object> row : rows) {
      if (row.isEmpty() || row.get(0) == null) {
        continue;
      }
      String table = String.valueOf(row.get(0)).trim();
      Long bytes = parseSize(row);
      if (bytes == null) {
        continue;
      }
      upsert(projectId, date, layer, db, table, bytes);
      count++;
    }
    return count;
  }

  /**
   * 表级字节量的候选采集语句,按序尝试、首个可用即采纳。
   *
   * <p>Doris 系 {@code SHOW DATA} 必须排在前面:反过来用 {@code information_schema} 打底的话,
   * Doris 侧 {@code DATA_LENGTH} 恒为 0,会让采集"成功"却产不出数据,又回到本方法修复前的静默空快照。
   *
   * <p>MySQL 不认识 {@code SHOW DATA},报语法错后落到这条标准视图查询。
   * 库名已过 {@link #SAFE_DB} 白名单才拼进 SQL,不引入注入面。
   */
  static List<String> statementsFor(String db) {
    if (!SAFE_DB.matcher(db).matches()) {
      throw new IllegalArgumentException("分层库名含非法字符，无法采集：" + db);
    }
    return List.of(
        "SHOW DATA FROM `" + db + "`",
        "SELECT TABLE_NAME, IFNULL(DATA_LENGTH, 0) + IFNULL(INDEX_LENGTH, 0)"
            + " FROM information_schema.TABLES"
            + " WHERE TABLE_SCHEMA = '" + db + "' AND TABLE_TYPE <> 'VIEW'");
  }

  private void upsert(Long projectId, LocalDate date, WarehouseLayer layer,
      String db, String table, long bytes) {
    LifecycleStorageSnapshotPO existing = snapshotMapper.selectOne(
        new LambdaQueryWrapper<LifecycleStorageSnapshotPO>()
            .eq(LifecycleStorageSnapshotPO::getProjectId, projectId)
            .eq(LifecycleStorageSnapshotPO::getSnapshotDate, date)
            .eq(LifecycleStorageSnapshotPO::getTableName, table)
            .last("LIMIT 1"));
    if (existing == null) {
      LifecycleStorageSnapshotPO po = new LifecycleStorageSnapshotPO();
      po.setProjectId(projectId);
      po.setSnapshotDate(date);
      po.setLayerCode(layer.code().toUpperCase());
      po.setDatasourceId(layer.datasourceId());
      po.setDatabaseName(db);
      po.setTableName(table);
      po.setSizeBytes(bytes);
      po.setCreateTime(LocalDateTime.now());
      snapshotMapper.insert(po);
    } else {
      existing.setSizeBytes(bytes);
      existing.setLayerCode(layer.code().toUpperCase());
      snapshotMapper.updateById(existing);
    }
  }

  /** 取行内首个可解析的 "1.23 GB" 形态大小;无法解析返回 null。 */
  public static Long parseSize(List<Object> row) {
    for (Object cell : row) {
      if (cell == null) {
        continue;
      }
      Matcher m = SIZE.matcher(String.valueOf(cell));
      if (m.find() && m.group(2) != null) {
        double v = Double.parseDouble(m.group(1));
        return Math.round(v * unitBytes(m.group(2).toUpperCase()));
      }
    }
    for (Object cell : row) {
      if (cell instanceof Number n && n.longValue() > 0) {
        return n.longValue();
      }
    }
    return null;
  }

  private static double unitBytes(String unit) {
    return switch (unit) {
      case "TB" -> 1024d * 1024 * 1024 * 1024;
      case "GB" -> 1024d * 1024 * 1024;
      case "MB" -> 1024d * 1024;
      case "KB" -> 1024d;
      default -> 1d;
    };
  }
}
