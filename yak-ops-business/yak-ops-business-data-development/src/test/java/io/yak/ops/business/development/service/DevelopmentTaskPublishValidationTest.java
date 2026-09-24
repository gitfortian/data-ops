package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.business.development.domain.DevelopmentTaskPublishValidation;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskDraftRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.development.task.DevelopmentTaskService;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginDescriptor;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DevelopmentTaskPublishValidationTest {

  private DevelopmentTaskDraftRepository draftRepository;
  private DevelopmentTaskService service;

  @BeforeEach
  void setUp() {
    DevelopmentNodeRepository nodeRepository = mock(DevelopmentNodeRepository.class);
    draftRepository = mock(DevelopmentTaskDraftRepository.class);
    DevelopmentTaskRevisionRepository revisionRepository = mock(DevelopmentTaskRevisionRepository.class);
    TaskCatalogService taskCatalogService = mock(TaskCatalogService.class);
    service = new DevelopmentTaskService(
        nodeRepository,
        draftRepository,
        revisionRepository,
        taskCatalogService,
        TaskPluginRegistry.from(List.of(new TestSqlPlugin())),
        new ObjectMapper());

    Instant now = Instant.parse("2026-09-24T00:00:00Z");
    when(nodeRepository.findById(1L)).thenReturn(Optional.of(
        new DevelopmentNode(1L, "Golden SQL", "SQL", null, null, true, now, now)));
  }

  @Test
  void preflightReturnsValidationForTheExactDraftRevision() {
    TaskDefinition definition = new TaskDefinition("SQL", 1, "select 1", "{}");
    when(draftRepository.findByNodeId(1L)).thenReturn(Optional.of(new DevelopmentTaskDraft(
        1L, definition, 7L, Instant.now(), Instant.now())));

    DevelopmentTaskPublishValidation result = service.validateForPublish(1L, 7L);

    assertEquals(1L, result.nodeId());
    assertEquals(7L, result.draftRevision());
    assertTrue(result.valid());
    assertTrue(result.issues().isEmpty());
  }

  @Test
  void preflightReturnsPluginIssuesWithoutPublishing() {
    TaskDefinition definition = new TaskDefinition("SQL", 1, "", "{}");
    when(draftRepository.findByNodeId(1L)).thenReturn(Optional.of(new DevelopmentTaskDraft(
        1L, definition, 8L, Instant.now(), Instant.now())));

    DevelopmentTaskPublishValidation result = service.validateForPublish(1L, 8L);

    assertFalse(result.valid());
    assertTrue(result.issues().stream()
        .anyMatch(issue -> "SQL_CONTENT_REQUIRED".equals(issue.code())));
  }

  @Test
  void preflightRejectsAStaleDraftRevisionInsteadOfValidatingAnotherDraft() {
    TaskDefinition definition = new TaskDefinition("SQL", 1, "select 2", "{}");
    when(draftRepository.findByNodeId(1L)).thenReturn(Optional.of(new DevelopmentTaskDraft(
        1L, definition, 9L, Instant.now(), Instant.now())));

    assertThrows(
        DevelopmentDraftConflictException.class,
        () -> service.validateForPublish(1L, 8L));
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
