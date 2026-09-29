package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskDraftRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginDescriptor;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DevelopmentTaskServiceTest {

  private DevelopmentNodeRepository nodeRepository;
  private DevelopmentTaskDraftRepository draftRepository;
  private DevelopmentTaskRevisionRepository revisionRepository;
  private TaskCatalogService taskCatalogService;
  private DevelopmentTaskService service;

  @BeforeEach
  void setUp() {
    nodeRepository = mock(DevelopmentNodeRepository.class);
    draftRepository = mock(DevelopmentTaskDraftRepository.class);
    revisionRepository = mock(DevelopmentTaskRevisionRepository.class);
    taskCatalogService = mock(TaskCatalogService.class);
    service = new DevelopmentTaskService(
        nodeRepository,
        draftRepository,
        revisionRepository,
        taskCatalogService,
        TaskPluginRegistry.from(List.of(new TestSqlPlugin())),
        new ObjectMapper());

    Instant now = Instant.parse("2026-08-12T00:00:00Z");
    when(nodeRepository.findById(1L)).thenReturn(Optional.of(
        new DevelopmentNode(1L, "今天统计", "SQL", null, null, false, now, now)));
    when(nodeRepository.findById(2L)).thenReturn(Optional.of(
        new DevelopmentNode(2L, "销售数据集", "DATASET", null, null, false, now, now)));
    when(nodeRepository.findById(3L)).thenReturn(Optional.of(
        new DevelopmentNode(3L, "订单查询 API", "DATA_SERVICE", null, null, false, now, now)));
  }

  @Test
  void saveDraftUsesOptimisticBaseRevisionAndMarksNodeConfigured() {
    TaskDefinition definition = new TaskDefinition("SQL", 1, "select 1", "{}");
    DevelopmentTaskDraft saved = new DevelopmentTaskDraft(
        1L, definition, 1L, Instant.now(), Instant.now());
    when(draftRepository.save(eq(1L), any(TaskDefinition.class), eq(0L)))
        .thenReturn(Optional.of(saved));

    DevelopmentTaskDraft result = service.saveDraft(
        1L, "sql", 1, "select 1", "{ }", 0L);

    assertEquals(1L, result.draftRevision());
    assertEquals("{}", result.definition().configJson());
    verify(nodeRepository).updateConfigured(1L, true);
  }

  @Test
  void saveDraftRejectsConcurrentOverwrite() {
    when(draftRepository.save(eq(1L), any(TaskDefinition.class), eq(2L)))
        .thenReturn(Optional.empty());

    assertThrows(
        DevelopmentDraftConflictException.class,
        () -> service.saveDraft(1L, "SQL", 1, "select 2", "{}", 2L));
  }

  @Test
  void outputResourcesCannotEnterTaskDraftOrPublishLifecycle() {
    for (long nodeId : List.of(2L, 3L)) {
      IllegalArgumentException draftException = assertThrows(
          IllegalArgumentException.class,
          () -> service.getDraft(nodeId));
      assertTrue(draftException.getMessage().contains("不是可执行开发任务"));

      IllegalArgumentException publishException = assertThrows(
          IllegalArgumentException.class,
          () -> service.publish(nodeId, 1L));
      assertTrue(publishException.getMessage().contains("不是可执行开发任务"));
    }
  }

  @Test
  void publishCreatesImmutableRevisionAndReconcilesTaskCatalog() {
    TaskDefinition definition = new TaskDefinition("SQL", 1, "select 1", "{}");
    DevelopmentTaskDraft draft = new DevelopmentTaskDraft(
        1L, definition, 3L, Instant.now(), Instant.now());
    when(draftRepository.findByNodeIdForUpdate(1L)).thenReturn(Optional.of(draft));
    when(revisionRepository.findLatestByNodeId(1L)).thenReturn(Optional.empty());
    when(revisionRepository.nextRevisionNo(1L)).thenReturn(1);
    when(revisionRepository.insert(eq(1L), eq(1), eq(3L), eq(definition), any(String.class)))
        .thenAnswer(invocation -> new DevelopmentTaskRevision(
            100L,
            1L,
            1,
            3L,
            definition,
            invocation.getArgument(4),
            Instant.now()));

    DevelopmentTaskRevision published = service.publish(1L, 3L);

    assertEquals(1, published.revisionNo());
    assertEquals(3L, published.sourceDraftRevision());
    assertEquals(64, published.checksum().length());
    verify(nodeRepository).updateConfigured(1L, true);
    verify(taskCatalogService).publish(
        TaskAssetSource.DATA_DEVELOPMENT,
        "1",
        null,
        "今天统计",
        "SQL",
        100L,
        1);

    // Re-publishing the unchanged draft reuses v1 but still repairs/advances catalog state idempotently.
    when(revisionRepository.findLatestByNodeId(1L)).thenReturn(Optional.of(published));
    DevelopmentTaskRevision repeated = service.publish(1L, 3L);
    assertEquals(published, repeated);
    verify(revisionRepository, times(1))
        .insert(eq(1L), eq(1), eq(3L), eq(definition), any(String.class));
    verify(taskCatalogService, times(2)).publish(
        TaskAssetSource.DATA_DEVELOPMENT,
        "1",
        null,
        "今天统计",
        "SQL",
        100L,
        1);
  }

  @Test
  void publishRejectsPluginValidationErrors() {
    TaskDefinition definition = new TaskDefinition("SQL", 1, "", "{}");
    DevelopmentTaskDraft draft = new DevelopmentTaskDraft(
        1L, definition, 1L, Instant.now(), Instant.now());
    when(draftRepository.findByNodeIdForUpdate(1L)).thenReturn(Optional.of(draft));

    DevelopmentTaskValidationException exception = assertThrows(
        DevelopmentTaskValidationException.class,
        () -> service.publish(1L, 1L));

    assertTrue(exception.issues().stream()
        .anyMatch(issue -> "SQL_CONTENT_REQUIRED".equals(issue.code())));
  }

  private static final class TestSqlPlugin implements TaskPlugin {

    @Override
    public TaskPluginDescriptor descriptor() {
      return new TaskPluginDescriptor("SQL", "SQL", "test", "1.0.0", 1, false, false);
    }

    @Override
    public TaskValidationResult validate(TaskDefinition definition) {
      if (definition.content() == null || definition.content().isBlank()) {
        return TaskValidationResult.invalid(
            new TaskValidationIssue("SQL_CONTENT_REQUIRED", "content", "SQL content required"));
      }
      return TaskValidationResult.ok();
    }
  }
}
