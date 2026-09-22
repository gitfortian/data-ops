package io.yak.ops.business.development.task;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskDefinition;
import org.springframework.stereotype.Component;

/** Appends/reuses immutable Task Revision and reconciles the Task Catalog publication projection. */
@Component
public class DevelopmentTaskPublisher {

  private final DevelopmentTaskRevisionRepository revisionRepository;
  private final DevelopmentNodeRepository nodeRepository;
  private final TaskCatalogService taskCatalogService;

  public DevelopmentTaskPublisher(
      DevelopmentTaskRevisionRepository revisionRepository,
      DevelopmentNodeRepository nodeRepository,
      TaskCatalogService taskCatalogService) {
    this.revisionRepository = revisionRepository;
    this.nodeRepository = nodeRepository;
    this.taskCatalogService = taskCatalogService;
  }

  public DevelopmentTaskRevision publish(
      DevelopmentNode node,
      DevelopmentTaskDraft draft,
      TaskDefinition definition,
      String checksum) {
    Long sourceProjectId = node.requireProjectId();
    DevelopmentTaskRevision latest = revisionRepository.findLatestByNodeId(node.id()).orElse(null);
    DevelopmentTaskRevision published;
    if (latest != null && latest.represents(draft.draftRevision(), checksum)) {
      published = latest;
    } else {
      int revisionNo = revisionRepository.nextRevisionNo(node.id());
      published = revisionRepository.insert(
          node.id(),
          revisionNo,
          draft.draftRevision(),
          definition,
          checksum);
    }

    nodeRepository.updateConfigured(node.id(), true);
    taskCatalogService.publish(
        TaskAssetSource.DATA_DEVELOPMENT,
        String.valueOf(node.id()),
        sourceProjectId,
        node.name(),
        definition.taskType(),
        published.id(),
        published.revisionNo());
    return published;
  }
}
