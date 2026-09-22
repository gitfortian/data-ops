package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.domain.DevelopmentTaskRevisionSummary;
import io.yak.ops.business.development.release.model.DevelopmentReleasePage;
import io.yak.ops.business.development.release.model.DevelopmentReleaseSummary;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DevelopmentReleaseServiceTest {

  private TaskCatalogService taskCatalogService;
  private DevelopmentTaskRevisionRepository revisionRepository;
  private DevelopmentReleaseService service;

  @BeforeEach
  void setUp() {
    taskCatalogService = mock(TaskCatalogService.class);
    revisionRepository = mock(DevelopmentTaskRevisionRepository.class);
    service = new DevelopmentReleaseService(taskCatalogService, revisionRepository);
  }

  @Test
  void listsOnlineAndOfflineAssetsWithCurrentAndLatestRevisionState() {
    TaskAsset online = asset(10L, 1L, "订单统计", TaskAssetStatus.ONLINE, 102L, 2);
    TaskAsset offline = asset(11L, 2L, "库存统计", TaskAssetStatus.OFFLINE, 201L, 1);
    DevelopmentTaskRevision onlineRevision = revision(102L, 1L, 2, "select 2");
    DevelopmentTaskRevision offlineRevision = revision(201L, 2L, 1, "select 1");

    when(taskCatalogService.list("DATA_DEVELOPMENT", "ONLINE", null)).thenReturn(List.of(online));
    when(taskCatalogService.list("DATA_DEVELOPMENT", "OFFLINE", null)).thenReturn(List.of(offline));
    when(taskCatalogService.list("DATA_DEVELOPMENT", "DISABLED", null)).thenReturn(List.of());
    when(revisionRepository.findById(102L)).thenReturn(Optional.of(onlineRevision));
    when(revisionRepository.findById(201L)).thenReturn(Optional.of(offlineRevision));
    when(revisionRepository.listByNodeId(1L)).thenReturn(List.of(summary(102L, 1L, 2)));
    when(revisionRepository.listByNodeId(2L)).thenReturn(List.of(summary(201L, 2L, 1)));

    DevelopmentReleasePage page = service.page(1, 20, "ALL", "SQL", null);

    assertEquals(2, page.total());
    assertEquals(1, page.onlineCount());
    assertEquals(1, page.offlineCount());
    assertEquals(2, page.records().size());
    assertEquals(2, page.records().get(0).currentRevisionNo());
  }

  @Test
  void switchesCatalogPointerToHistoricalImmutableRevision() {
    TaskAsset currentAsset = asset(10L, 1L, "订单统计", TaskAssetStatus.ONLINE, 102L, 2);
    TaskAsset rolledBack = asset(10L, 1L, "订单统计", TaskAssetStatus.ONLINE, 101L, 1);
    DevelopmentTaskRevision revision1 = revision(101L, 1L, 1, "select 1");

    when(taskCatalogService.get(10L)).thenReturn(currentAsset);
    when(revisionRepository.findByRevisionNo(1L, 1)).thenReturn(Optional.of(revision1));
    when(taskCatalogService.publish(
        TaskAssetSource.DATA_DEVELOPMENT,
        "1",
        null,
        "订单统计",
        "SQL",
        101L,
        1)).thenReturn(rolledBack);
    when(revisionRepository.findById(101L)).thenReturn(Optional.of(revision1));
    when(revisionRepository.listByNodeId(1L)).thenReturn(List.of(
        summary(102L, 1L, 2),
        summary(101L, 1L, 1)));

    DevelopmentReleaseSummary result = service.activate(10L, 1);

    assertEquals(1, result.currentRevisionNo());
    assertEquals(2, result.latestRevisionNo());
    assertTrue(result.hasNewerRevision());
    verify(taskCatalogService).publish(
        TaskAssetSource.DATA_DEVELOPMENT,
        "1",
        null,
        "订单统计",
        "SQL",
        101L,
        1);
  }

  private TaskAsset asset(
      long assetId,
      long nodeId,
      String name,
      TaskAssetStatus status,
      long revisionId,
      int revisionNo) {
    Instant now = Instant.parse("2026-08-13T06:00:00Z").plusSeconds(assetId);
    return new TaskAsset(
        assetId,
        TaskAssetSource.DATA_DEVELOPMENT,
        String.valueOf(nodeId),
        null,
        name,
        "SQL",
        status,
        new TaskRevisionRef(assetId, revisionId, revisionNo),
        now.minusSeconds(60),
        now);
  }

  private DevelopmentTaskRevision revision(long id, long nodeId, int revisionNo, String content) {
    return new DevelopmentTaskRevision(
        id,
        nodeId,
        revisionNo,
        revisionNo,
        new TaskDefinition("SQL", 1, content, "{}"),
        "checksum-" + revisionNo,
        Instant.parse("2026-08-13T06:00:00Z").plusSeconds(revisionNo));
  }

  private DevelopmentTaskRevisionSummary summary(long id, long nodeId, int revisionNo) {
    return new DevelopmentTaskRevisionSummary(
        id,
        nodeId,
        revisionNo,
        revisionNo,
        "checksum-" + revisionNo,
        Instant.parse("2026-08-13T06:00:00Z").plusSeconds(revisionNo));
  }
}
