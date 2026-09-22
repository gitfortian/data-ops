package io.yak.ops.business.development.task;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.taskcatalog.spi.TaskAssetRevisionProvider;
import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import io.yak.ops.spi.task.model.TaskAssetSource;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resolves Data Development immutable revisions for source-neutral Task Catalog consumers. */
@Component
public class DataDevelopmentTaskRevisionProvider implements TaskAssetRevisionProvider {

  private final DevelopmentTaskRevisionRepository revisionRepository;
  private final DevelopmentNodeRepository nodeRepository;

  public DataDevelopmentTaskRevisionProvider(
      DevelopmentTaskRevisionRepository revisionRepository,
      DevelopmentNodeRepository nodeRepository) {
    this.revisionRepository = revisionRepository;
    this.nodeRepository = nodeRepository;
  }

  @Override
  public TaskAssetSource source() {
    return TaskAssetSource.DATA_DEVELOPMENT;
  }

  @Override
  public Optional<TaskSourceRevision> resolve(String sourceRef, long revisionId) {
    Long nodeId = parseNodeId(sourceRef);
    Optional<DevelopmentNode> node = nodeRepository.findById(nodeId);
    if (node.isEmpty()) return Optional.empty();
    Long sourceProjectId = node.orElseThrow().requireProjectId();
    return revisionRepository.findById(revisionId)
        .filter(revision -> nodeId.equals(revision.nodeId()))
        .map(revision -> toSourceRevision(revision, sourceProjectId));
  }

  private TaskSourceRevision toSourceRevision(
      DevelopmentTaskRevision revision, Long sourceProjectId) {
    return new TaskSourceRevision(
        revision.id(),
        revision.revisionNo(),
        revision.definition(),
        revision.checksum(),
        sourceProjectId);
  }

  private Long parseNodeId(String sourceRef) {
    if (sourceRef == null || sourceRef.isBlank()) {
      throw new IllegalArgumentException("Data Development sourceRef 不能为空");
    }
    try {
      long value = Long.parseLong(sourceRef.trim());
      if (value <= 0L) throw new NumberFormatException("not positive");
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("非法 Data Development sourceRef：" + sourceRef, exception);
    }
  }
}
