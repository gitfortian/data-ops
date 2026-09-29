package io.yak.ops.business.development.lineage;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.development.service.DevelopmentSqlLineageService;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the durable outbox and restores each task's Project Space before business IO. */
@Component
public class DevelopmentLineageWorker {
  private static final Logger LOGGER = LoggerFactory.getLogger(DevelopmentLineageWorker.class);
  private final DevelopmentLineageOutbox outbox;
  private final DevelopmentNodeRepository nodes;
  private final DevelopmentTaskRevisionRepository revisions;
  private final DevelopmentSqlLineageService lineage;
  private final DevelopmentLineageWriteTransaction writer;
  private final ProjectContextScope projectScope;

  public DevelopmentLineageWorker(
      DevelopmentLineageOutbox outbox,
      DevelopmentNodeRepository nodes,
      DevelopmentTaskRevisionRepository revisions,
      DevelopmentSqlLineageService lineage,
      DevelopmentLineageWriteTransaction writer,
      ProjectContextScope projectScope) {
    this.outbox = outbox;
    this.nodes = nodes;
    this.revisions = revisions;
    this.lineage = lineage;
    this.writer = writer;
    this.projectScope = projectScope;
  }

  @Scheduled(fixedDelayString = "${yak.development.lineage-outbox.poll-delay-ms:1000}")
  public void poll() {
    for (DevelopmentLineageOutbox.Task task : outbox.due(20)) process(task);
  }

  void process(DevelopmentLineageOutbox.Task task) {
    projectScope.run(
        new ProjectContext(task.projectId(), null),
        () -> processInProject(task));
  }

  private void processInProject(DevelopmentLineageOutbox.Task task) {
    if (!outbox.claim(task)) return;
    try {
      DevelopmentNode node = nodes.findById(task.nodeId()).orElseThrow();
      if (!task.projectId().equals(node.requireProjectId())) {
        throw new IllegalStateException(
            "Lineage outbox project does not match development node: task="
                + task.taskId()
                + ", taskProject="
                + task.projectId()
                + ", nodeProject="
                + node.projectId());
      }
      DevelopmentTaskRevision revision = revisions.findById(task.revisionId()).orElseThrow();
      if (!node.id().equals(revision.nodeId())) {
        throw new IllegalStateException(
            "Lineage outbox revision does not belong to development node: task="
                + task.taskId()
                + ", nodeId="
                + node.id()
                + ", revisionNodeId="
                + revision.nodeId());
      }
      DevelopmentSqlLineageService.PreparedLineage prepared = lineage.prepare(node, revision);
      writer.writeIfLatest(node, revision, prepared);
      outbox.complete(task);
    } catch (Throwable failure) {
      outbox.fail(task, failure);
      LOGGER.warn("SQL lineage outbox task {} failed; publish remains committed", task.taskId(), failure);
    }
  }
}
