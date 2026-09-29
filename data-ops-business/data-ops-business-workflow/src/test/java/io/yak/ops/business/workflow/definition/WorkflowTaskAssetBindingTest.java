package io.yak.ops.business.workflow.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import io.yak.ops.business.workflow.repository.NoopWorkflowDefinitionRepository;
import io.yak.ops.business.workflow.runtime.WorkflowRuntime;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionCreateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowVersionVO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextException;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WorkflowTaskAssetBindingTest {

  @Test
  void pinsPublishedRevisionUntilUserExplicitlyUpgradesDraft() {
    WorkflowRuntime runtimeService = mock(WorkflowRuntime.class);
    TaskRegistry taskRegistry = mock(TaskRegistry.class);
    TaskCatalogService taskCatalogService = mock(TaskCatalogService.class);

    TaskDefinition definitionV1 = new TaskDefinition(
        "SQL",
        1,
        "select 1 as version",
        "{\"dataSourceId\":\"1\"}");
    TaskDefinition definitionV2 = new TaskDefinition(
        "SQL",
        1,
        "select 2 as version",
        "{\"dataSourceId\":\"1\"}");

    TaskAsset assetV1 = asset(7L, "SQL", 101L, 1);
    TaskAsset assetV2 = asset(7L, "SQL", 102L, 2);
    AtomicReference<TaskAsset> currentAsset = new AtomicReference<>(assetV1);

    when(taskCatalogService.get(12L)).thenAnswer(invocation -> currentAsset.get());
    when(taskCatalogService.resolveRevision(12L, 101L)).thenReturn(new TaskAssetRevision(
        assetV1,
        new TaskSourceRevision(101L, 1, definitionV1, "checksum-v1", 7L)));
    when(taskCatalogService.resolveRevision(12L, 102L)).thenReturn(new TaskAssetRevision(
        assetV2,
        new TaskSourceRevision(102L, 2, definitionV2, "checksum-v2", 7L)));

    WorkflowDefinitionManager service = projectAwareService(
        runtimeService, taskRegistry, taskCatalogService, 7L);

    WorkflowDefinitionVO created = service.create(
        new WorkflowDefinitionCreateDTO("日报工作流", "验证固定 TaskRevision"));

    WorkflowDefinitionVO draftV1 = service.update(
        created.id(),
        updateRequest(101L, 1));
    assertEquals(1, draftV1.nodes().getFirst().taskRevisionNo());
    assertEquals(1, draftV1.nodes().getFirst().latestTaskRevisionNo());
    assertFalse(draftV1.nodes().getFirst().taskRevisionUpdateAvailable());

    WorkflowDefinitionVO publishedV1 = service.online(created.id());
    assertEquals(1, publishedV1.activeVersionNo());
    assertFalse(publishedV1.draftChanged());

    currentAsset.set(assetV2);

    WorkflowDefinitionVO stillPinnedToV1 = service.get(created.id());
    assertEquals(1, stillPinnedToV1.nodes().getFirst().taskRevisionNo());
    assertEquals(2, stillPinnedToV1.nodes().getFirst().latestTaskRevisionNo());
    assertTrue(stillPinnedToV1.nodes().getFirst().taskRevisionUpdateAvailable());
    assertEquals(1, stillPinnedToV1.activeVersionNo());
    assertFalse(stillPinnedToV1.draftChanged());

    WorkflowDefinitionVO upgradedDraft = service.upgradeTaskRevision(created.id(), "task-node-1");
    assertEquals(2, upgradedDraft.nodes().getFirst().taskRevisionNo());
    assertEquals(2, upgradedDraft.nodes().getFirst().latestTaskRevisionNo());
    assertFalse(upgradedDraft.nodes().getFirst().taskRevisionUpdateAvailable());
    assertEquals(1, upgradedDraft.activeVersionNo());
    assertTrue(upgradedDraft.draftChanged());

    WorkflowDefinitionVO publishedV2 = service.online(created.id());
    assertEquals(2, publishedV2.activeVersionNo());
    assertFalse(publishedV2.draftChanged());

    WorkflowVersionVO latestVersion = service.versions(created.id()).getFirst();
    assertEquals(2, latestVersion.versionNo());
    assertEquals(2L, latestVersion.taskBindings().getFirst().taskVersion());
    assertEquals("task-asset:12", latestVersion.taskBindings().getFirst().taskId());
  }

  @Test
  void rejectsDataDevelopmentOutputAssetsAtWorkflowPublishBoundary() {
    WorkflowRuntime runtimeService = mock(WorkflowRuntime.class);
    TaskRegistry taskRegistry = mock(TaskRegistry.class);
    TaskCatalogService taskCatalogService = mock(TaskCatalogService.class);
    WorkflowDefinitionManager service = projectAwareService(
        runtimeService, taskRegistry, taskCatalogService, 7L);

    WorkflowDefinitionVO created = service.create(
        new WorkflowDefinitionCreateDTO("边界工作流", "输出资源不能参与编排"));

    for (String taskType : List.of("DATASET", "DATA_SERVICE")) {
      TaskAsset asset = asset(7L, taskType, 101L, 1);
      TaskDefinition definition = new TaskDefinition(
          taskType,
          1,
          "{}",
          "{}");
      when(taskCatalogService.get(12L)).thenReturn(asset);
      when(taskCatalogService.resolveRevision(12L, 101L)).thenReturn(new TaskAssetRevision(
          asset,
          new TaskSourceRevision(101L, 1, definition, "checksum", 7L)));
      service.update(created.id(), updateRequest(101L, 1));

      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> service.online(created.id()));

      assertTrue(exception.getMessage().contains("不能进入工作流编排"));
    }
  }

  @Test
  void rejectsTaskAssetOwnedByAnotherProject() {
    WorkflowRuntime runtimeService = mock(WorkflowRuntime.class);
    TaskRegistry taskRegistry = mock(TaskRegistry.class);
    TaskCatalogService taskCatalogService = mock(TaskCatalogService.class);
    WorkflowDefinitionManager service = projectAwareService(
        runtimeService, taskRegistry, taskCatalogService, 7L);
    TaskAsset otherProjectAsset = asset(9L, "SQL", 101L, 1);
    when(taskCatalogService.get(12L)).thenReturn(otherProjectAsset);

    WorkflowDefinitionVO created = service.create(
        new WorkflowDefinitionCreateDTO("跨 Project 工作流", "验证绑定隔离"));
    service.update(created.id(), updateRequest(101L, 1));

    assertThrows(ProjectContextException.class, () -> service.online(created.id()));
  }

  private static WorkflowDefinitionManager projectAwareService(
      WorkflowRuntime runtimeService,
      TaskRegistry taskRegistry,
      TaskCatalogService taskCatalogService,
      long projectId) {
    CurrentProject currentProject =
        () -> Optional.of(new ProjectContext(projectId, "Project " + projectId));
    return new WorkflowDefinitionManager(
        runtimeService,
        taskRegistry,
        taskCatalogService,
        NoopWorkflowDefinitionRepository.INSTANCE,
        currentProject);
  }

  private static WorkflowDefinitionUpdateDTO updateRequest(long revisionId, int revisionNo) {
    WorkflowDefinitionUpdateDTO.NodeDTO node = new WorkflowDefinitionUpdateDTO.NodeDTO(
        "task-node-1",
        "task-asset:12",
        12L,
        revisionId,
        revisionNo,
        160D,
        120D,
        1,
        0L,
        0L,
        0L,
        Map.of(),
        "ALL_SUCCESS",
        "FAIL_WORKFLOW");
    return new WorkflowDefinitionUpdateDTO(
        "日报工作流",
        "验证固定 TaskRevision",
        List.of(node),
        List.of(),
        Map.of(),
        Map.of(),
        0L,
        "CONTINUE_INDEPENDENT_BRANCHES");
  }

  private static TaskAsset asset(long projectId, String taskType, long revisionId, int revisionNo) {
    Instant now = Instant.parse("2026-08-12T00:00:00Z");
    return new TaskAsset(
        12L,
        TaskAssetSource.DATA_DEVELOPMENT,
        "10001",
        projectId,
        "今天统计",
        taskType,
        TaskAssetStatus.ONLINE,
        new TaskRevisionRef(12L, revisionId, revisionNo),
        now,
        now);
  }
}
