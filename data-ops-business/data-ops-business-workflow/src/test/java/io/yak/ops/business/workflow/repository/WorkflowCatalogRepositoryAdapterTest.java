package io.yak.ops.business.workflow.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.dao.WorkflowCatalogDao;
import io.yak.ops.business.workflow.domain.WorkflowNodeSpec;
import io.yak.ops.business.workflow.domain.WorkflowRunSpec;
import io.yak.ops.business.workflow.repository.WorkflowDefinitionRepository.DefinitionRecord;
import io.yak.ops.business.workflow.repository.WorkflowDefinitionRepository.VersionRecord;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowVersionPO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class WorkflowCatalogRepositoryAdapterTest {

  @Test
  void shouldKeepCanvasCoordinatesOutOfPublishedRuntimeJsonAndRestoreSpec() throws Exception {
    WorkflowCatalogDao dao = Mockito.mock(WorkflowCatalogDao.class);
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    WorkflowCatalogRepositoryAdapter adapter =
        new WorkflowCatalogRepositoryAdapter(dao, new WorkflowJsonCodec(objectMapper));
    Instant now = Instant.parse("2026-08-09T10:00:00Z");
    WorkflowNodeSpec node = new WorkflowNodeSpec(
        "node-a", "task-a", 321D, 654D, 2, 3L, 4L, 5L,
        Map.of("bizDate", "$workflow.bizDate"), "ALL_SUCCESS", "FAIL_WORKFLOW");
    WorkflowRunSpec runSpec = new WorkflowRunSpec(
        "published", List.of(node), List.of(), Map.of("bizDate", "2026-08-09"), 60L,
        "CONTINUE_INDEPENDENT_BRANCHES");
    VersionRecord version = new VersionRecord(
        "version-1", "workflow-1", 1, 2L, runSpec, Map.of("canvas", "meta"),
        Map.of("node-a", new TaskVersionSnapshot(
            "task-a", "Task A", "SYNC", 7L, "digest", "{}", "{}")),
        now);
    DefinitionRecord definition = new DefinitionRecord(
        "workflow-1", "published", null, "ONLINE", "CONTINUE_INDEPENDENT_BRANCHES",
        List.of(node), List.of(), Map.of(), Map.of(), 60L, 2L, 1, "version-1",
        null, null, now, now);

    adapter.publish(definition, version);

    ArgumentCaptor<WorkflowVersionPO> versionCaptor = ArgumentCaptor.forClass(WorkflowVersionPO.class);
    verify(dao).insertVersion(versionCaptor.capture());
    verify(dao).upsertDefinition(any(WorkflowDefinitionPO.class));
    WorkflowVersionPO stored = versionCaptor.getValue();
    JsonNode json = objectMapper.readTree(stored.getRunRequestJson());
    JsonNode storedNode = json.path("nodes").get(0);
    assertThat(storedNode.has("positionX")).isFalse();
    assertThat(storedNode.has("positionY")).isFalse();
    assertThat(storedNode.path("taskId").asText()).isEqualTo("task-a");

    when(dao.selectPublishedVersions("workflow-1")).thenReturn(List.of(stored));
    VersionRecord restored = adapter.loadVersions("workflow-1").get(0);
    assertThat(restored.runSpec().nodes()).hasSize(1);
    assertThat(restored.runSpec().nodes().get(0).taskId()).isEqualTo("task-a");
    assertThat(restored.runSpec().nodes().get(0).positionX()).isZero();
    assertThat(restored.runSpec().nodes().get(0).positionY()).isZero();
  }
}
