package io.yak.ops.business.lifecycle.preview;

import io.yak.ops.business.lifecycle.binding.ModelTtlBindingService;
import io.yak.ops.business.lifecycle.binding.ModelTtlResolution;
import io.yak.ops.business.lifecycle.dispatch.TtlSqlGateway;
import io.yak.ops.business.lifecycle.exception.LifecycleException;
import io.yak.ops.common.enums.lifecycle.LifecycleErrorCode;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.StorageType;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * TTL 预览(ticket 84):SHOW PARTITIONS 归类热/冷/将删,D6 按能力降级标 estimated,
 * 批量出确认令牌供下发校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtlPreviewService {

  private static final int MAX_PARTITIONS = 5000;
  private static final int DELETED_LIST_CAP = 50;

  public record PartitionSplit(
      Integer total,
      int hotCount,
      int coldCount,
      int deletedCount,
      String hotRange,
      String coldRange,
      List<String> deletedPartitions,
      boolean estimated,
      String estimatedReason) {}

  public record ModelPreview(
      Long modelId,
      String modelName,
      String tableName,
      String policyName,
      String policySource,
      Integer hotDays,
      Integer destroyDays,
      String statement,
      boolean dispatchable,
      String notDispatchableReason,
      PartitionSplit split,
      LocalDate nextCleanup) {}

  public record PreviewBatch(
      String confirmToken,
      long tokenExpiresInSeconds,
      List<ModelPreview> previews) {}

  private final ModelTtlBindingService bindingService;
  private final TtlSqlGateway gateway;
  private final ConfirmTokenService confirmTokenService;
  private final CurrentProject currentProject;

  public PreviewBatch preview(List<Long> modelIds) {
    if (modelIds == null || modelIds.isEmpty()) {
      throw new LifecycleException(LifecycleErrorCode.MODEL_NOT_FOUND, "未选择模型");
    }
    List<ModelPreview> previews = modelIds.stream().map(this::previewOne).toList();
    Long projectId = currentProject.requireProjectId();
    String token = confirmTokenService.issue(projectId, modelIds);
    return new PreviewBatch(token, ConfirmTokenService.WINDOW_SECONDS, previews);
  }

  /** 下发快照计数也复用这里的归类。 */
  public ModelPreview previewOne(Long modelId) {
    ModelTtlResolution r = bindingService.resolve(modelId);
    if (r.policy() == null) {
      return new ModelPreview(modelId, r.model().name(), r.model().tableName(), null,
          r.bindingSource().name(), null, null, null, false, r.notPreviewableReason(),
          new PartitionSplit(null, 0, 0, 0, null, null, List.of(), true, "尚未配置 TTL 策略"),
          null);
    }
    PartitionSplit split;
    boolean live = r.previewable()
        && r.statement().storageType() == StorageType.DORIS && gateway.available();
    if (live) {
      List<String> names;
      try {
        names = showPartitionNames(r.layer().datasourceId(),
            qualified(r.layer().databaseName(), r.model().tableName()));
      } catch (LifecycleException e) {
        // 表不存在/无权限等:降级估算而不阻断整个向导
        names = List.of();
        log.warn("SHOW PARTITIONS failed for model {}: {}", modelId, e.getMessage());
      }
      split = classify(names, LocalDate.now(), r.policy().getHotDays(),
          r.policy().getColdDays(), r.policy().getDestroyDays());
    } else {
      split = new PartitionSplit(null, 0, 0, 0, null, null, List.of(), true,
          r.previewable() ? "目标存储暂不支持平台列取分区,按策略推算" : r.notPreviewableReason());
    }
    LocalDate nextCleanup = r.policy().getDestroyDays() == null
        ? null : LocalDate.now().plusDays(1);
    return new ModelPreview(modelId, r.model().name(), r.model().tableName(),
        r.policy().getPolicyName(), r.bindingSource().name(),
        r.policy().getHotDays(), r.policy().getDestroyDays(),
        r.statement().statement(), r.previewable(), r.notPreviewableReason(),
        split, nextCleanup);
  }

  /** 归类纯函数(ticket 84 单测口径):hot≤hotDays 热,>destroyDays 将删,其余冷。 */
  public static PartitionSplit classify(List<String> rawNames, LocalDate today,
      Integer hotDays, Integer coldDays, Integer destroyDays) {
    List<String> dated = rawNames.stream().filter(n -> parsePartitionDate(n) != null).toList();
    List<String> hot = new ArrayList<>();
    List<String> cold = new ArrayList<>();
    List<String> deleted = new ArrayList<>();
    for (String name : dated) {
      long daysAgo = ChronoUnit.DAYS.between(parsePartitionDate(name), today);
      if (destroyDays != null && daysAgo > destroyDays) {
        deleted.add(name);
      } else if (hotDays != null && daysAgo <= hotDays) {
        hot.add(name);
      } else {
        cold.add(name);
      }
    }
    return new PartitionSplit(dated.size(), hot.size(), cold.size(), deleted.size(),
        range(hot), range(cold), deleted.stream().sorted().limit(DELETED_LIST_CAP).toList(),
        false, dated.size() < rawNames.size() ? "部分分区名无法解析日期,未计入归类" : null);
  }

  /** 兼容 p20260918 / 20260918 / dt=2026-09-18 / 202609 / 2026 等命名。 */
  public static LocalDate parsePartitionDate(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String v = raw.trim();
    int eq = v.lastIndexOf('=');
    if (eq >= 0) {
      v = v.substring(eq + 1);
    }
    v = v.replace("-", "").replace("/", "").replace("_", "");
    if (!v.isEmpty() && (v.charAt(0) == 'p' || v.charAt(0) == 'P') && v.length() > 4) {
      v = v.substring(1);
    }
    try {
      if (v.length() == 8 && v.chars().allMatch(Character::isDigit)) {
        return LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE);
      }
      if (v.length() == 6 && v.chars().allMatch(Character::isDigit)) {
        return LocalDate.parse(v + "01", DateTimeFormatter.BASIC_ISO_DATE);
      }
      if (v.length() == 4 && v.chars().allMatch(Character::isDigit)) {
        return LocalDate.of(Integer.parseInt(v), 1, 1);
      }
    } catch (RuntimeException e) {
      return null;
    }
    return null;
  }

  private List<String> showPartitionNames(Long datasourceId, String qualifiedTable) {
    List<List<Object>> rows = gateway.query(datasourceId,
        "SHOW PARTITIONS FROM " + qualifiedTable, MAX_PARTITIONS);
    return rows.stream()
        .flatMap(row -> row.stream())
        .filter(Objects::nonNull)
        .map(String::valueOf)
        .filter(v -> parsePartitionDate(v) != null)
        .distinct()
        .toList();
  }

  private static String qualified(String database, String table) {
    String db = database == null ? "" : database.replace("`", "").trim();
    String tbl = table == null ? "" : table.replace("`", "").trim();
    return db.isEmpty() ? "`" + tbl + "`" : "`" + db + "`.`" + tbl + "`";
  }

  private static String range(List<String> names) {
    if (names.isEmpty()) {
      return null;
    }
    List<String> sorted = names.stream().sorted().toList();
    return sorted.size() == 1 ? sorted.get(0) : sorted.get(0) + " ~ " + sorted.get(sorted.size() - 1);
  }
}
