package io.yak.ops.business.development.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.development.service.DevelopmentSqlLineageService;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DevelopmentLineageWorkerTest {
  @Test
  void restoresProjectBeforePreparingAndWritingLineage() {
    DevelopmentLineageOutbox outbox = mock(DevelopmentLineageOutbox.class);
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    DevelopmentTaskRevisionRepository revisions = mock(DevelopmentTaskRevisionRepository.class);
    DevelopmentSqlLineageService lineage = mock(DevelopmentSqlLineageService.class);
    DevelopmentLineageWriteTransaction writer = mock(DevelopmentLineageWriteTransaction.class);
    RecordingProjectScope projectScope = new RecordingProjectScope();
    DevelopmentLineageOutbox.Task task = new DevelopmentLineageOutbox.Task("task", 7L, 1, 11, 0);
    DevelopmentNode node = node(7L);
    DevelopmentTaskRevision revision = revision(11, 1);
    DevelopmentSqlLineageService.PreparedLineage prepared =
        mock(DevelopmentSqlLineageService.PreparedLineage.class);
    when(outbox.claim(task)).thenReturn(true);
    when(nodes.findById(1L)).thenReturn(Optional.of(node));
    when(revisions.findById(11L)).thenReturn(Optional.of(revision));
    when(lineage.prepare(node, revision)).thenReturn(prepared);

    new DevelopmentLineageWorker(outbox, nodes, revisions, lineage, writer, projectScope)
        .process(task);

    assertEquals(7L, projectScope.lastProjectId);
    var order = inOrder(lineage, writer, outbox);
    order.verify(lineage).prepare(node, revision);
    order.verify(writer).writeIfLatest(node, revision, prepared);
    order.verify(outbox).complete(task);
  }

  @Test
  void failureIsRetriedWithoutEscapingWorker() {
    DevelopmentLineageOutbox outbox = mock(DevelopmentLineageOutbox.class);
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    DevelopmentLineageOutbox.Task task = new DevelopmentLineageOutbox.Task("task", 7L, 1, 11, 2);
    RuntimeException failure = new RuntimeException("catalog unavailable");
    when(outbox.claim(task)).thenReturn(true);
    when(nodes.findById(1L)).thenThrow(failure);

    new DevelopmentLineageWorker(
            outbox,
            nodes,
            mock(DevelopmentTaskRevisionRepository.class),
            mock(DevelopmentSqlLineageService.class),
            mock(DevelopmentLineageWriteTransaction.class),
            new RecordingProjectScope())
        .process(task);

    verify(outbox).fail(task, failure);
    verify(outbox, never()).complete(task);
  }

  @Test
  void rejectsMismatchedNodeProjectBeforeWritingLineage() {
    DevelopmentLineageOutbox outbox = mock(DevelopmentLineageOutbox.class);
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    DevelopmentLineageWriteTransaction writer = mock(DevelopmentLineageWriteTransaction.class);
    DevelopmentLineageOutbox.Task task = new DevelopmentLineageOutbox.Task("task", 7L, 1, 11, 0);
    when(outbox.claim(task)).thenReturn(true);
    when(nodes.findById(1L)).thenReturn(Optional.of(node(8L)));

    new DevelopmentLineageWorker(
            outbox,
            nodes,
            mock(DevelopmentTaskRevisionRepository.class),
            mock(DevelopmentSqlLineageService.class),
            writer,
            new RecordingProjectScope())
        .process(task);

    verify(outbox).fail(org.mockito.ArgumentMatchers.eq(task), org.mockito.ArgumentMatchers.any());
    verify(writer, never()).writeIfLatest(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }

  private static DevelopmentNode node(Long projectId) {
    return new DevelopmentNode(
        1L, "sql", "SQL", projectId, null, true, Instant.now(), Instant.now());
  }

  private static DevelopmentTaskRevision revision(long id, int number) {
    return new DevelopmentTaskRevision(
        id,
        1L,
        number,
        number,
        new TaskDefinition(
            "SQL", 1, "insert into b select * from a", "{\"dataSourceId\":\"1\"}"),
        "checksum",
        Instant.now());
  }

  private static final class RecordingProjectScope implements ProjectContextScope {
    private Long lastProjectId;

    @Override
    public <T> T call(ProjectContext context, Supplier<T> action) {
      lastProjectId = context.projectId();
      return action.get();
    }
  }
}
