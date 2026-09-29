package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.DevelopmentDatasetFacade;
import io.yak.ops.business.dataset.DevelopmentDatasetFacade.FieldDraft;
import io.yak.ops.business.dataset.DevelopmentDatasetFacade.NodeDataset;
import io.yak.ops.business.development.dataset.DevelopmentDatasetNodeService;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DevelopmentDatasetNodeServiceTest {

  @Test
  void getListsPublishedSqlSourcesWithoutGraphTopology() {
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DevelopmentDatasetFacade datasets = mock(DevelopmentDatasetFacade.class);
    DevelopmentDatasetNodeService service = new DevelopmentDatasetNodeService(nodes, catalog, datasets);

    when(nodes.findById(501L)).thenReturn(Optional.of(node(501L, "sales_dataset", "DATASET", false, 7L)));
    when(nodes.findById(101L)).thenReturn(Optional.of(node(101L, "sales.sql", "SQL", true, 7L)));
    when(catalog.list("DATA_DEVELOPMENT", "ONLINE", null)).thenReturn(List.of(
        taskAsset(11L, 101L, 71L, 3, TaskAssetStatus.ONLINE, "SQL", 7L),
        taskAsset(12L, 102L, 72L, 1, TaskAssetStatus.ONLINE, "SHELL", 7L)));
    when(datasets.findByDevelopmentNodeId(501L)).thenReturn(Optional.empty());

    DevelopmentDatasetNodeService.DatasetNodeContext result = service.get(501L);

    assertEquals(1, result.availableSources().size());
    assertEquals("11", result.availableSources().get(0).taskAssetId());
    assertEquals("sales.sql", result.availableSources().get(0).nodeName());
    assertNull(result.selectedSource());
  }

  @Test
  void previewUsesExplicitPublishedSqlSource() {
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DevelopmentDatasetFacade datasets = mock(DevelopmentDatasetFacade.class);
    DevelopmentDatasetNodeService service = new DevelopmentDatasetNodeService(nodes, catalog, datasets);

    when(nodes.findById(501L)).thenReturn(Optional.of(node(501L, "sales_dataset", "DATASET", false, 7L)));
    when(nodes.findById(101L)).thenReturn(Optional.of(node(101L, "sales.sql", "SQL", true, 7L)));
    when(catalog.get(11L)).thenReturn(taskAsset(
        11L, 101L, 71L, 3, TaskAssetStatus.ONLINE, "SQL", 7L));
    FieldDraft field = new FieldDraft(
        null, "sales_amount", "sales_amount", "NUMBER", true, null, "MEASURE");
    when(datasets.preview(11L)).thenReturn(List.of(field));

    List<FieldDraft> result = service.preview(501L, 11L);

    assertEquals(List.of(field), result);
    verify(datasets).preview(11L);
  }

  @Test
  void saveFreezesExplicitSqlSourceAndMarksNodeConfigured() {
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DevelopmentDatasetFacade datasets = mock(DevelopmentDatasetFacade.class);
    DevelopmentDatasetNodeService service = new DevelopmentDatasetNodeService(nodes, catalog, datasets);

    DevelopmentNode datasetNode = node(501L, "sales_dataset", "DATASET", false, 7L);
    DevelopmentNode configuredNode = node(501L, "sales_dataset", "DATASET", true, 7L);
    DevelopmentNode sqlNode = node(101L, "sales.sql", "SQL", true, 7L);
    TaskAsset asset = taskAsset(11L, 101L, 71L, 3, TaskAssetStatus.ONLINE, "SQL", 7L);
    when(nodes.findById(501L)).thenReturn(Optional.of(datasetNode), Optional.of(configuredNode));
    when(nodes.findById(101L)).thenReturn(Optional.of(sqlNode));
    when(catalog.get(11L)).thenReturn(asset);
    when(catalog.list("DATA_DEVELOPMENT", "ONLINE", null)).thenReturn(List.of(asset));
    when(nodes.updateConfigured(501L, true)).thenReturn(true);
    NodeDataset saved = new NodeDataset(
        "501", "21", 7L, "sales_dataset", "销售数据", "ONLINE", null,
        List.of(), List.of(), null, null, null, Instant.EPOCH, Instant.EPOCH);
    when(datasets.save(eq(501L), eq(11L), eq("sales_dataset"), eq("销售数据"), anyList()))
        .thenReturn(saved);

    DevelopmentDatasetNodeService.DatasetNodeContext result = service.save(
        501L, 11L, "销售数据", List.of());

    assertTrue(result.configured());
    assertEquals("11", result.selectedSource().taskAssetId());
    assertEquals(3, result.selectedSource().revisionNo());
    assertEquals("21", result.dataset().datasetId());
    verify(datasets).save(eq(501L), eq(11L), eq("sales_dataset"), eq("销售数据"), anyList());
    verify(nodes).updateConfigured(501L, true);
  }

  @Test
  void rejectsSqlSourceFromAnotherProject() {
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DevelopmentDatasetFacade datasets = mock(DevelopmentDatasetFacade.class);
    DevelopmentDatasetNodeService service = new DevelopmentDatasetNodeService(nodes, catalog, datasets);

    when(nodes.findById(501L)).thenReturn(Optional.of(node(501L, "sales_dataset", "DATASET", false, 7L)));
    when(catalog.get(11L)).thenReturn(taskAsset(
        11L, 101L, 71L, 3, TaskAssetStatus.ONLINE, "SQL", 8L));

    IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> service.preview(501L, 11L));

    assertEquals("Dataset 只能选择同项目的 SQL 来源", error.getMessage());
  }

  @Test
  void getRejectsDatasetOwnedByAnotherProject() {
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DevelopmentDatasetFacade datasets = mock(DevelopmentDatasetFacade.class);
    DevelopmentDatasetNodeService service = new DevelopmentDatasetNodeService(nodes, catalog, datasets);

    when(nodes.findById(501L)).thenReturn(Optional.of(node(501L, "sales_dataset", "DATASET", true, 7L)));
    when(datasets.findByDevelopmentNodeId(501L)).thenReturn(Optional.of(new NodeDataset(
        "501", "21", 8L, "sales_dataset", "销售数据", "ONLINE", null,
        List.of(), List.of(), null, null, null, Instant.EPOCH, Instant.EPOCH)));

    IllegalStateException error = assertThrows(
        IllegalStateException.class,
        () -> service.get(501L));

    assertTrue(error.getMessage().contains("Dataset 与数据开发节点 Project 不一致"));
    assertTrue(error.getMessage().contains("nodeProjectId=7"));
    assertTrue(error.getMessage().contains("datasetProjectId=8"));
  }

  @Test
  void saveRejectsDatasetProjectDriftBeforeMarkingNodeConfigured() {
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DevelopmentDatasetFacade datasets = mock(DevelopmentDatasetFacade.class);
    DevelopmentDatasetNodeService service = new DevelopmentDatasetNodeService(nodes, catalog, datasets);

    DevelopmentNode datasetNode = node(501L, "sales_dataset", "DATASET", false, 7L);
    when(nodes.findById(501L)).thenReturn(Optional.of(datasetNode));
    when(datasets.save(eq(501L), eq("10"), eq("select 1"), eq("sales_dataset"), eq("销售数据"), anyList()))
        .thenReturn(new NodeDataset(
            "501", "21", 8L, "sales_dataset", "销售数据", "DRAFT", null,
            List.of(), List.of(), null, null, null, Instant.EPOCH, Instant.EPOCH));

    IllegalStateException error = assertThrows(
        IllegalStateException.class,
        () -> service.save(501L, "10", "select 1", "销售数据", List.of()));

    assertTrue(error.getMessage().contains("Dataset 与数据开发节点 Project 不一致"));
    verify(nodes, never()).updateConfigured(501L, true);
  }

  private static DevelopmentNode node(
      long id,
      String name,
      String type,
      boolean configured,
      Long projectId) {
    return new DevelopmentNode(
        id, name, type, projectId, null, configured, Instant.EPOCH, Instant.EPOCH);
  }

  private static TaskAsset taskAsset(
      long assetId,
      long sourceNodeId,
      long revisionId,
      int revisionNo,
      TaskAssetStatus status,
      String taskType,
      Long projectId) {
    return new TaskAsset(
        assetId,
        TaskAssetSource.DATA_DEVELOPMENT,
        String.valueOf(sourceNodeId),
        projectId,
        taskType.equals("SQL") ? "sales.sql" : "task",
        taskType,
        status,
        new TaskRevisionRef(assetId, revisionId, revisionNo),
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
