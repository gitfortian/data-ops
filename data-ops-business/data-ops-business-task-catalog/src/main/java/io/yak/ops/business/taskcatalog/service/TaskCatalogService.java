package io.yak.ops.business.taskcatalog.service;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.repository.TaskAssetRepository;
import io.yak.ops.business.taskcatalog.spi.TaskAssetRevisionProvider;
import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Application service for the published task asset catalog. */
@Service
@ConditionalOnDataSourceEnabled
public class TaskCatalogService {

  private static final Set<String> DATA_DEVELOPMENT_TASK_TYPES = Set.of(
      "SQL",
      "SHELL",
      "HTTP",
      "PYTHON",
      "JAVA");

  private final TaskAssetRepository repository;
  private final Map<TaskAssetSource, TaskAssetRevisionProvider> revisionProviders;

  @Autowired
  public TaskCatalogService(
      TaskAssetRepository repository,
      List<TaskAssetRevisionProvider> revisionProviders) {
    this.repository = repository;
    Map<TaskAssetSource, TaskAssetRevisionProvider> discovered = new LinkedHashMap<>();
    for (TaskAssetRevisionProvider provider : revisionProviders) {
      TaskAssetRevisionProvider existing = discovered.putIfAbsent(provider.source(), provider);
      if (existing != null) {
        throw new IllegalStateException(
            "重复的 TaskAsset revision provider：" + provider.source()
                + " -> " + existing.getClass().getName()
                + ", " + provider.getClass().getName());
      }
    }
    this.revisionProviders = Map.copyOf(discovered);
  }

  /** Backward-compatible constructor for focused unit tests. */
  public TaskCatalogService(TaskAssetRepository repository) {
    this(repository, List.of());
  }

  public TaskAsset publish(
      TaskAssetSource source,
      String sourceRef,
      Long sourceProjectId,
      String name,
      String taskType,
      long revisionId,
      int revisionNo) {
    if (source == null) throw new IllegalArgumentException("任务资产来源不能为空");
    if (revisionId <= 0L) throw new IllegalArgumentException("任务版本 ID 必须大于 0");
    if (revisionNo <= 0) throw new IllegalArgumentException("任务版本号必须大于 0");
    String normalizedTaskType = normalizeTaskType(taskType);
    requirePublishableTaskType(source, normalizedTaskType);
    return repository.upsertPublished(
        source,
        normalizeSourceRef(sourceRef),
        normalizeProjectId(sourceProjectId),
        normalizeName(name),
        normalizedTaskType,
        revisionId,
        revisionNo);
  }

  public List<TaskAsset> list(String source, String status, String keyword) {
    return repository.list(
        parseSource(source),
        parseStatus(status),
        normalizeKeyword(keyword));
  }

  public TaskAsset get(long assetId) {
    if (assetId <= 0L) throw new IllegalArgumentException("taskAssetId 必须大于 0");
    return repository.findById(assetId)
        .orElseThrow(() -> new IllegalArgumentException("任务资产不存在：" + assetId));
  }

  public TaskAssetRevision resolveRevision(long assetId, long revisionId) {
    if (revisionId <= 0L) throw new IllegalArgumentException("taskRevisionId 必须大于 0");
    TaskAsset asset = get(assetId);
    TaskAssetRevisionProvider provider = revisionProviders.get(asset.source());
    if (provider == null) {
      throw new IllegalStateException("任务资产来源尚未接入版本解析：" + asset.source());
    }
    TaskSourceRevision revision = provider.resolve(asset.sourceRef(), revisionId)
        .orElseThrow(() -> new IllegalArgumentException(
            "任务版本不存在或不属于当前资产：asset=" + assetId + "，revision=" + revisionId));
    if (!asset.taskType().equalsIgnoreCase(revision.definition().taskType())) {
      throw new IllegalStateException(
          "任务资产类型与版本类型不一致：asset=" + asset.taskType()
              + "，revision=" + revision.definition().taskType());
    }
    if (asset.projectId() != null
        && revision.sourceProjectId() != null
        && !Objects.equals(asset.projectId(), revision.sourceProjectId())) {
      throw new IllegalStateException(
          "任务资产 Project 与来源版本 Project 不一致：assetProject=" + asset.projectId()
              + "，sourceProject=" + revision.sourceProjectId());
    }
    return new TaskAssetRevision(asset, revision);
  }

  public Optional<TaskAsset> findBySource(TaskAssetSource source, String sourceRef) {
    if (source == null) return Optional.empty();
    return repository.findBySource(source, normalizeSourceRef(sourceRef));
  }

  /** Keeps an already-published asset aligned with its source node without creating draft assets. */
  public void updateSourceMetadata(
      TaskAssetSource source,
      String sourceRef,
      Long sourceProjectId,
      String name,
      String taskType) {
    if (source == null) throw new IllegalArgumentException("任务资产来源不能为空");
    repository.updateSourceMetadata(
        source,
        normalizeSourceRef(sourceRef),
        normalizeProjectId(sourceProjectId),
        normalizeName(name),
        normalizeTaskType(taskType));
  }

  /** Offline assets disappear from new orchestration discovery while retaining their revision pointer. */
  public void offlineSource(TaskAssetSource source, String sourceRef) {
    if (source == null) throw new IllegalArgumentException("任务资产来源不能为空");
    repository.updateStatus(
        source,
        normalizeSourceRef(sourceRef),
        TaskAssetStatus.OFFLINE);
  }

  private TaskAssetSource parseSource(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return TaskAssetSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知任务资产来源：" + value, exception);
    }
  }

  private TaskAssetStatus parseStatus(String value) {
    String normalized = value == null || value.isBlank() ? "ONLINE" : value.trim();
    try {
      return TaskAssetStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知任务资产状态：" + value, exception);
    }
  }

  private String normalizeSourceRef(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("sourceRef 不能为空");
    String normalized = value.trim();
    if (normalized.length() > 128) throw new IllegalArgumentException("sourceRef 不能超过 128 个字符");
    return normalized;
  }

  private String normalizeName(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("任务资产名称不能为空");
    String normalized = value.trim();
    if (normalized.length() > 200) throw new IllegalArgumentException("任务资产名称不能超过 200 个字符");
    return normalized;
  }

  private String normalizeTaskType(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("taskType 不能为空");
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (normalized.length() > 64) throw new IllegalArgumentException("taskType 不能超过 64 个字符");
    return normalized;
  }

  private Long normalizeProjectId(Long value) {
    return value == null || value <= 0L ? null : value;
  }

  private String normalizeKeyword(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > 200) throw new IllegalArgumentException("搜索关键字不能超过 200 个字符");
    return normalized;
  }

  private void requirePublishableTaskType(TaskAssetSource source, String taskType) {
    if (source != TaskAssetSource.DATA_DEVELOPMENT) return;
    if (DATA_DEVELOPMENT_TASK_TYPES.contains(taskType)) return;
    throw new IllegalArgumentException(
        "数据开发输出资源不能发布到 Task Catalog：taskType=" + taskType);
  }
}
