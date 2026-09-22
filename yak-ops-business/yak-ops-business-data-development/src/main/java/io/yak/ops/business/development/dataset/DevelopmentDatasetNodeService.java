package io.yak.ops.business.development.dataset;

import io.yak.ops.business.dataset.DevelopmentDatasetFacade;
import io.yak.ops.business.dataset.DevelopmentDatasetFacade.FieldDraft;
import io.yak.ops.business.dataset.DevelopmentDatasetFacade.NodeDataset;
import io.yak.ops.business.dataset.DevelopmentDatasetFacade.PreviewResult;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentNodeType;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dataset node application service. New editors own datasource + SQL directly. */
@Service
public class DevelopmentDatasetNodeService {

  private final DevelopmentNodeRepository nodeRepository;
  private final TaskCatalogService taskCatalogService;
  private final DevelopmentDatasetFacade datasetFacade;

  public DevelopmentDatasetNodeService(
      DevelopmentNodeRepository nodeRepository,
      TaskCatalogService taskCatalogService,
      DevelopmentDatasetFacade datasetFacade) {
    this.nodeRepository = nodeRepository;
    this.taskCatalogService = taskCatalogService;
    this.datasetFacade = datasetFacade;
  }

  public DatasetNodeContext get(long nodeId) {
    DevelopmentNode datasetNode = requireDatasetNode(nodeId);
    NodeDataset dataset = datasetFacade.findByDevelopmentNodeId(nodeId)
        .map(candidate -> requireSameProject(datasetNode, candidate))
        .orElse(null);
    return context(datasetNode, dataset, selectedSource(dataset));
  }

  /** Standalone editor preview; no SQL TaskAsset is required. */
  public List<FieldDraft> preview(long nodeId, String dataSourceId, String sql) {
    requireDatasetNode(nodeId);
    return datasetFacade.preview(dataSourceId, sql);
  }

  /** Execute Dataset-owned SQL and return both field contract and actual result rows. */
  public PreviewResult query(long nodeId, String dataSourceId, String sql) {
    requireDatasetNode(nodeId);
    return datasetFacade.previewQuery(dataSourceId, sql);
  }

  /** Standalone editor save; DatasetVersion freezes datasource + SQL + field contract. */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DatasetNodeContext save(
      long nodeId,
      String dataSourceId,
      String sql,
      String description,
      List<FieldDraft> fields) {
    DevelopmentNode datasetNode = requireDatasetNode(nodeId);
    NodeDataset saved = requireSameProject(
        datasetNode,
        datasetFacade.save(
            nodeId, dataSourceId, sql, datasetNode.name(), description, fields));
    markConfigured(datasetNode);
    DevelopmentNode refreshed = nodeRepository.findById(nodeId).orElse(datasetNode);
    return context(refreshed, saved, null);
  }

  /** Legacy TaskAsset preview retained for compatibility during migration. */
  public List<FieldDraft> preview(long nodeId, long sourceTaskAssetId) {
    DevelopmentNode datasetNode = requireDatasetNode(nodeId);
    TaskAsset source = requireSelectableSqlAsset(datasetNode, sourceTaskAssetId);
    return datasetFacade.preview(source.id());
  }

  /** Legacy TaskAsset save retained for compatibility during migration. */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DatasetNodeContext save(
      long nodeId,
      long sourceTaskAssetId,
      String description,
      List<FieldDraft> fields) {
    DevelopmentNode datasetNode = requireDatasetNode(nodeId);
    TaskAsset source = requireSelectableSqlAsset(datasetNode, sourceTaskAssetId);
    NodeDataset saved = requireSameProject(
        datasetNode,
        datasetFacade.save(
            nodeId, source.id(), datasetNode.name(), description, fields));
    markConfigured(datasetNode);
    DevelopmentNode refreshed = nodeRepository.findById(nodeId).orElse(datasetNode);
    return context(refreshed, saved, toSnapshot(source));
  }

  /** Publish a new immutable version from the current draft. */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DatasetNodeContext publish(long nodeId) {
    DevelopmentNode datasetNode = requireDatasetNode(nodeId);
    NodeDataset published = requireSameProject(
        datasetNode,
        datasetFacade.publishVersion(nodeId));
    DevelopmentNode refreshed = nodeRepository.findById(nodeId).orElse(datasetNode);
    return context(refreshed, published, null);
  }

  private void markConfigured(DevelopmentNode datasetNode) {
    if (!datasetNode.configured() && !nodeRepository.updateConfigured(datasetNode.id(), true)) {
      throw new IllegalStateException("Dataset Node 配置状态更新失败：" + datasetNode.id());
    }
  }

  private DatasetNodeContext context(
      DevelopmentNode datasetNode,
      NodeDataset dataset,
      SourceSnapshot selectedSource) {
    return new DatasetNodeContext(
        String.valueOf(datasetNode.id()),
        datasetNode.name(),
        datasetNode.configured() || dataset != null,
        availableSources(datasetNode),
        selectedSource,
        dataset);
  }

  /** Legacy source list remains in the response temporarily; the new UI no longer consumes it. */
  private List<SourceSnapshot> availableSources(DevelopmentNode datasetNode) {
    return taskCatalogService.list("DATA_DEVELOPMENT", "ONLINE", null).stream()
        .filter(asset -> "SQL".equalsIgnoreCase(asset.taskType()))
        .filter(asset -> sameProject(asset.projectId(), datasetNode.projectId()))
        .map(this::toSnapshotIfValidSqlNode)
        .flatMap(Optional::stream)
        .sorted(Comparator
            .comparing(SourceSnapshot::nodeName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(SourceSnapshot::nodeId))
        .toList();
  }

  private SourceSnapshot selectedSource(NodeDataset dataset) {
    if (dataset == null || dataset.currentVersion() == null) return null;
    String assetId = dataset.currentVersion().sourceTaskAssetId();
    if (assetId == null || assetId.isBlank() || "0".equals(assetId)) return null;
    try {
      return toSnapshot(taskCatalogService.get(Long.parseLong(assetId)));
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private TaskAsset requireSelectableSqlAsset(DevelopmentNode datasetNode, long sourceTaskAssetId) {
    if (sourceTaskAssetId <= 0L) throw new IllegalArgumentException("sourceTaskAssetId 必须大于 0");
    TaskAsset asset = taskCatalogService.get(sourceTaskAssetId);
    if (asset.source() != TaskAssetSource.DATA_DEVELOPMENT) {
      throw new IllegalArgumentException("Dataset 只能选择数据开发 SQL 来源");
    }
    if (!"SQL".equalsIgnoreCase(asset.taskType())) {
      throw new IllegalArgumentException("Dataset 当前仅支持 SQL 来源");
    }
    if (asset.status() != TaskAssetStatus.ONLINE) {
      throw new IllegalArgumentException("Dataset 只能选择 ONLINE SQL 来源");
    }
    if (!sameProject(asset.projectId(), datasetNode.projectId())) {
      throw new IllegalArgumentException("Dataset 只能选择同项目的 SQL 来源");
    }
    DevelopmentNode sourceNode = requireSourceSqlNode(asset);
    if (!sameProject(sourceNode.projectId(), datasetNode.projectId())) {
      throw new IllegalArgumentException("Dataset 只能选择同项目的 SQL 节点");
    }
    return asset;
  }

  private DevelopmentNode requireSourceSqlNode(TaskAsset asset) {
    long sourceNodeId;
    try {
      sourceNodeId = Long.parseLong(asset.sourceRef());
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("SQL TaskAsset sourceRef 不是有效节点 ID：" + asset.sourceRef());
    }
    DevelopmentNode sourceNode = nodeRepository.findById(sourceNodeId)
        .orElseThrow(() -> new IllegalStateException("SQL 来源节点不存在：" + sourceNodeId));
    if (!DevelopmentNodeType.SQL.name().equalsIgnoreCase(sourceNode.type())) {
      throw new IllegalStateException("TaskAsset 来源节点不是 SQL：" + sourceNodeId);
    }
    return sourceNode;
  }

  private Optional<SourceSnapshot> toSnapshotIfValidSqlNode(TaskAsset asset) {
    try {
      return Optional.of(toSnapshot(asset, requireSourceSqlNode(asset)));
    } catch (RuntimeException exception) {
      return Optional.empty();
    }
  }

  private SourceSnapshot toSnapshot(TaskAsset asset) {
    DevelopmentNode sourceNode = nodeRepository.findById(parseSourceNodeId(asset)).orElse(null);
    return sourceNode == null
        ? new SourceSnapshot(
            asset.sourceRef(), asset.name(), String.valueOf(asset.id()), asset.status().name(),
            String.valueOf(asset.currentRevision().taskRevisionId()), asset.currentRevision().revisionNo())
        : toSnapshot(asset, sourceNode);
  }

  private SourceSnapshot toSnapshot(TaskAsset asset, DevelopmentNode sourceNode) {
    return new SourceSnapshot(
        String.valueOf(sourceNode.id()), sourceNode.name(), String.valueOf(asset.id()),
        asset.status().name(), String.valueOf(asset.currentRevision().taskRevisionId()),
        asset.currentRevision().revisionNo());
  }

  private long parseSourceNodeId(TaskAsset asset) {
    try {
      return Long.parseLong(asset.sourceRef());
    } catch (NumberFormatException exception) {
      return -1L;
    }
  }

  private DevelopmentNode requireDatasetNode(long nodeId) {
    if (nodeId <= 0L) throw new IllegalArgumentException("nodeId 必须大于 0");
    DevelopmentNode node = nodeRepository.findById(nodeId)
        .orElseThrow(() -> new IllegalArgumentException("数据开发节点不存在：" + nodeId));
    DevelopmentNodeType type = DevelopmentNodeType.tryParse(node.type()).orElse(null);
    if (type != DevelopmentNodeType.DATASET) {
      throw new IllegalArgumentException("当前节点不是 Dataset Node：" + nodeId);
    }
    node.requireProjectId();
    return node;
  }

  private NodeDataset requireSameProject(DevelopmentNode datasetNode, NodeDataset dataset) {
    long nodeProjectId = datasetNode.requireProjectId();
    if (dataset.projectId() <= 0L || dataset.projectId() != nodeProjectId) {
      throw new IllegalStateException(
          "Dataset 与数据开发节点 Project 不一致：nodeId="
              + datasetNode.id()
              + ", nodeProjectId="
              + nodeProjectId
              + ", datasetId="
              + dataset.datasetId()
              + ", datasetProjectId="
              + dataset.projectId());
    }
    return dataset;
  }

  private boolean sameProject(Long left, Long right) {
    return Objects.equals(normalizeProjectId(left), normalizeProjectId(right));
  }

  private Long normalizeProjectId(Long value) {
    return value == null || value <= 0L ? null : value;
  }

  public record DatasetNodeContext(
      String nodeId,
      String nodeName,
      boolean configured,
      List<SourceSnapshot> availableSources,
      SourceSnapshot selectedSource,
      NodeDataset dataset) {
  }

  public record SourceSnapshot(
      String nodeId,
      String nodeName,
      String taskAssetId,
      String status,
      String revisionId,
      Integer revisionNo) {
  }
}
