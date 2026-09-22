package io.yak.ops.business.development.node;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentNodeName;
import io.yak.ops.business.development.domain.DevelopmentNodeType;
import io.yak.ops.business.development.repository.DevelopmentDirectoryRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for lightweight data-development resource nodes. */
@Service
public class DevelopmentNodeService {

  private final DevelopmentNodeRepository repository;
  private final DevelopmentDirectoryRepository directoryRepository;
  private final TaskCatalogService taskCatalogService;

  public DevelopmentNodeService(
      DevelopmentNodeRepository repository,
      DevelopmentDirectoryRepository directoryRepository,
      TaskCatalogService taskCatalogService) {
    this.repository = repository;
    this.directoryRepository = directoryRepository;
    this.taskCatalogService = taskCatalogService;
  }

  public List<DevelopmentNode> list() {
    return repository.list();
  }

  /** Lightweight overview count that avoids materializing editor/publish metadata for every node. */
  public long count() {
    return repository.count();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentNode create(
      String name,
      String type,
      Long directoryId) {
    DevelopmentNodeName nodeName = DevelopmentNodeName.of(name);
    DevelopmentNodeType nodeType = DevelopmentNodeType.require(type);
    Long normalizedDirectoryId = normalizeDirectoryId(directoryId);

    if (normalizedDirectoryId != null
        && directoryRepository.findById(normalizedDirectoryId).isEmpty()) {
      throw new IllegalArgumentException("数据开发目录不存在：" + normalizedDirectoryId);
    }
    if (repository.existsByName(normalizedDirectoryId, nodeName.value())) {
      throw new IllegalStateException("当前目录下已存在同名节点：" + nodeName.value());
    }

    return repository.insert(
        nodeName.value(),
        nodeType.name(),
        normalizedDirectoryId,
        false);
  }

  /**
   * Compatibility entry for pre-Stage-5A internal callers. The supplied projectId is intentionally
   * ignored; Project Root ownership is resolved only from trusted CurrentProject in the repository.
   */
  @Deprecated
  public DevelopmentNode create(
      String name,
      String type,
      Long ignoredProjectId,
      Long directoryId) {
    return create(name, type, directoryId);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentNode rename(Long id, String name) {
    DevelopmentNode current = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("节点不存在：" + id));
    DevelopmentNodeName nodeName = DevelopmentNodeName.of(name);
    if (current.name().equals(nodeName.value())) return current;
    if (repository.existsByName(current.directoryId(), nodeName.value())) {
      throw new IllegalStateException("当前目录下已存在同名节点：" + nodeName.value());
    }
    if (!repository.updateName(id, nodeName.value())) {
      throw new IllegalStateException("节点重命名失败：" + id);
    }
    DevelopmentNode renamed = repository.findById(id)
        .orElseThrow(() -> new IllegalStateException("节点重命名成功但无法重新读取：" + id));
    if (renamed.nodeType().isProcessing()) {
      taskCatalogService.updateSourceMetadata(
          TaskAssetSource.DATA_DEVELOPMENT,
          String.valueOf(renamed.id()),
          renamed.projectId(),
          renamed.name(),
          renamed.type());
    }
    return renamed;
  }

  /** Records the human who most recently changed this node and returns refreshed tree metadata. */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentNode recordUpdater(Long id, String operatorName) {
    DevelopmentNode current = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("节点不存在：" + id));
    String normalizedOperator = normalizeOperator(operatorName);
    if (!repository.updateUpdatedBy(id, normalizedOperator)) {
      return current;
    }
    return repository.findById(id).orElse(current);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    DevelopmentNode current = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("节点不存在：" + id));
    if (!repository.deleteById(id)) {
      throw new IllegalStateException("节点删除失败：" + id);
    }
    if (current.nodeType().isProcessing()) {
      taskCatalogService.offlineSource(
          TaskAssetSource.DATA_DEVELOPMENT,
          String.valueOf(current.id()));
    }
  }

  /** Moves a node to a different directory. */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentNode move(Long id, Long targetDirectoryId) {
    DevelopmentNode current = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("节点不存在：" + id));
    Long normalizedDirectoryId = normalizeDirectoryId(targetDirectoryId);

    if (normalizedDirectoryId != null
        && directoryRepository.findById(normalizedDirectoryId).isEmpty()) {
      throw new IllegalArgumentException("目标目录不存在：" + normalizedDirectoryId);
    }
    if (normalizedDirectoryId != null && normalizedDirectoryId.equals(current.directoryId())) {
      return current;
    }
    if (normalizedDirectoryId == null && current.directoryId() == null) {
      return current;
    }
    if (repository.existsByName(normalizedDirectoryId, current.name())) {
      throw new IllegalStateException("目标目录下已存在同名节点：" + current.name());
    }
    if (!repository.updateDirectoryId(id, normalizedDirectoryId)) {
      throw new IllegalStateException("节点移动失败：" + id);
    }
    return repository.findById(id)
        .orElseThrow(() -> new IllegalStateException("节点移动成功但无法重新读取：" + id));
  }

  private Long normalizeDirectoryId(Long directoryId) {
    return directoryId == null || directoryId <= 0L ? null : directoryId;
  }

  private String normalizeOperator(String operatorName) {
    if (operatorName == null || operatorName.isBlank()) return "unknown";
    String normalized = operatorName.trim();
    return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
  }
}
