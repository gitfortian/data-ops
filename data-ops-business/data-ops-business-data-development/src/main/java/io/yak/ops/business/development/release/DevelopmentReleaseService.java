package io.yak.ops.business.development.release;

import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.domain.DevelopmentTaskRevisionSummary;
import io.yak.ops.business.development.release.model.DevelopmentReleaseDetail;
import io.yak.ops.business.development.release.model.DevelopmentReleasePage;
import io.yak.ops.business.development.release.model.DevelopmentReleaseSummary;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Release-center facade for immutable data-development revisions and Task Catalog state. */
@Service
public class DevelopmentReleaseService {

  private static final int MAX_PAGE_SIZE = 100;

  private final TaskCatalogService taskCatalogService;
  private final DevelopmentTaskRevisionRepository revisionRepository;

  public DevelopmentReleaseService(
      TaskCatalogService taskCatalogService,
      DevelopmentTaskRevisionRepository revisionRepository) {
    this.taskCatalogService = taskCatalogService;
    this.revisionRepository = revisionRepository;
  }

  public DevelopmentReleasePage page(
      int pageNo,
      int pageSize,
      String status,
      String taskType,
      String keyword) {
    int normalizedPageNo = Math.max(1, pageNo);
    int normalizedPageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
    String normalizedType = normalizeTaskType(taskType);
    String normalizedStatus = normalizeStatus(status);

    List<TaskAsset> online = filterType(
        taskCatalogService.list("DATA_DEVELOPMENT", "ONLINE", keyword), normalizedType);
    List<TaskAsset> offline = filterType(
        taskCatalogService.list("DATA_DEVELOPMENT", "OFFLINE", keyword), normalizedType);
    List<TaskAsset> disabled = filterType(
        taskCatalogService.list("DATA_DEVELOPMENT", "DISABLED", keyword), normalizedType);

    List<TaskAsset> selected = switch (normalizedStatus) {
      case "ONLINE" -> new ArrayList<>(online);
      case "OFFLINE" -> new ArrayList<>(offline);
      case "DISABLED" -> new ArrayList<>(disabled);
      default -> {
        List<TaskAsset> values = new ArrayList<>(online.size() + offline.size());
        values.addAll(online);
        values.addAll(offline);
        yield values;
      }
    };
    selected.sort(this::compareAsset);

    long total = selected.size();
    long offset = (long) (normalizedPageNo - 1) * normalizedPageSize;
    int from = (int) Math.min((long) selected.size(), offset);
    int to = Math.min(selected.size(), from + normalizedPageSize);
    List<DevelopmentReleaseSummary> records = selected.subList(from, to).stream()
        .map(this::toSummary)
        .toList();

    return new DevelopmentReleasePage(
        records,
        total,
        normalizedPageNo,
        normalizedPageSize,
        online.size(),
        offline.size(),
        disabled.size());
  }

  public DevelopmentReleaseDetail get(long assetId) {
    TaskAsset asset = requireDataDevelopmentAsset(assetId);
    Long nodeId = nodeId(asset);
    DevelopmentTaskRevision current = currentRevision(asset, nodeId);
    List<DevelopmentTaskRevisionSummary> revisions = revisionRepository.listByNodeId(nodeId);
    return new DevelopmentReleaseDetail(toSummary(asset, current, revisions), current, revisions);
  }

  public DevelopmentReleaseSummary offline(long assetId) {
    TaskAsset asset = requireDataDevelopmentAsset(assetId);
    if (asset.status() == TaskAssetStatus.OFFLINE) return toSummary(asset);
    if (asset.status() == TaskAssetStatus.DISABLED) {
      throw new IllegalArgumentException("已禁用任务不能在发布中心执行下线操作");
    }
    taskCatalogService.offlineSource(TaskAssetSource.DATA_DEVELOPMENT, asset.sourceRef());
    return toSummary(requireDataDevelopmentAsset(assetId));
  }

  public DevelopmentReleaseSummary online(long assetId) {
    TaskAsset asset = requireDataDevelopmentAsset(assetId);
    if (asset.status() == TaskAssetStatus.ONLINE) return toSummary(asset);
    ensureNotDisabled(asset);
    DevelopmentTaskRevision current = currentRevision(asset, nodeId(asset));
    return toSummary(taskCatalogService.publish(
        TaskAssetSource.DATA_DEVELOPMENT,
        asset.sourceRef(),
        asset.projectId(),
        asset.name(),
        asset.taskType(),
        current.id(),
        current.revisionNo()));
  }

  /** Switches the catalog pointer to any immutable historical revision; also brings the task online. */
  public DevelopmentReleaseSummary activate(long assetId, int revisionNo) {
    if (revisionNo <= 0) throw new IllegalArgumentException("revisionNo 必须大于 0");
    TaskAsset asset = requireDataDevelopmentAsset(assetId);
    ensureNotDisabled(asset);
    Long nodeId = nodeId(asset);
    DevelopmentTaskRevision target = revisionRepository.findByRevisionNo(nodeId, revisionNo)
        .orElseThrow(() -> new IllegalArgumentException(
            "发布版本不存在：nodeId=" + nodeId + ", revisionNo=" + revisionNo));

    if (asset.status() == TaskAssetStatus.ONLINE
        && Objects.equals(asset.currentRevision().taskRevisionId(), target.id())) {
      return toSummary(asset);
    }

    TaskAsset updated = taskCatalogService.publish(
        TaskAssetSource.DATA_DEVELOPMENT,
        asset.sourceRef(),
        asset.projectId(),
        asset.name(),
        asset.taskType(),
        target.id(),
        target.revisionNo());
    return toSummary(updated);
  }

  private TaskAsset requireDataDevelopmentAsset(long assetId) {
    if (assetId <= 0L) throw new IllegalArgumentException("assetId 必须大于 0");
    TaskAsset asset = taskCatalogService.get(assetId);
    if (asset.source() != TaskAssetSource.DATA_DEVELOPMENT) {
      throw new IllegalArgumentException("当前任务资产不属于数据开发：" + assetId);
    }
    return asset;
  }

  private DevelopmentReleaseSummary toSummary(TaskAsset asset) {
    Long nodeId = nodeId(asset);
    DevelopmentTaskRevision current = currentRevision(asset, nodeId);
    List<DevelopmentTaskRevisionSummary> revisions = revisionRepository.listByNodeId(nodeId);
    return toSummary(asset, current, revisions);
  }

  private DevelopmentReleaseSummary toSummary(
      TaskAsset asset,
      DevelopmentTaskRevision current,
      List<DevelopmentTaskRevisionSummary> revisions) {
    int latestRevisionNo = revisions.isEmpty()
        ? current.revisionNo()
        : revisions.get(0).revisionNo();
    return new DevelopmentReleaseSummary(
        asset.id(),
        current.nodeId(),
        asset.name(),
        asset.taskType(),
        asset.status(),
        current.id(),
        current.revisionNo(),
        latestRevisionNo,
        latestRevisionNo > current.revisionNo(),
        current.checksum(),
        current.createTime(),
        asset.updateTime());
  }

  private DevelopmentTaskRevision currentRevision(TaskAsset asset, Long nodeId) {
    Long revisionId = asset.currentRevision().taskRevisionId();
    DevelopmentTaskRevision revision = revisionRepository.findById(revisionId)
        .orElseThrow(() -> new IllegalStateException(
            "发布中心引用的版本不存在：assetId=" + asset.id() + ", revisionId=" + revisionId));
    if (!nodeId.equals(revision.nodeId())) {
      throw new IllegalStateException(
          "发布中心版本与节点不一致：assetId=" + asset.id() + ", revisionId=" + revisionId);
    }
    return revision;
  }

  private Long nodeId(TaskAsset asset) {
    try {
      long value = Long.parseLong(asset.sourceRef());
      if (value <= 0L) throw new NumberFormatException("not positive");
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("数据开发任务资产 sourceRef 不是有效节点 ID：" + asset.sourceRef());
    }
  }

  private List<TaskAsset> filterType(List<TaskAsset> assets, String taskType) {
    if (taskType == null) return new ArrayList<>(assets);
    return assets.stream()
        .filter(asset -> taskType.equalsIgnoreCase(asset.taskType()))
        .toList();
  }

  private String normalizeTaskType(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (normalized.length() > 64) throw new IllegalArgumentException("taskType 不能超过 64 个字符");
    return normalized;
  }

  private String normalizeStatus(String value) {
    if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())) return "ALL";
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    try {
      TaskAssetStatus.valueOf(normalized);
      return normalized;
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知发布状态：" + value, exception);
    }
  }

  private void ensureNotDisabled(TaskAsset asset) {
    if (asset.status() == TaskAssetStatus.DISABLED) {
      throw new IllegalArgumentException("已禁用任务不能直接重新上线或切换版本");
    }
  }

  private int compareAsset(TaskAsset left, TaskAsset right) {
    Instant leftTime = left.updateTime();
    Instant rightTime = right.updateTime();
    if (leftTime == null && rightTime == null) return Long.compare(right.id(), left.id());
    if (leftTime == null) return 1;
    if (rightTime == null) return -1;
    int timeCompare = rightTime.compareTo(leftTime);
    return timeCompare != 0 ? timeCompare : Long.compare(right.id(), left.id());
  }
}
