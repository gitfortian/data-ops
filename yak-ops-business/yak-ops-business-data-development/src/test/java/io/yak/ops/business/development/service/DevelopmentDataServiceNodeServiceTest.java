package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDefinition;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDraft;
import io.yak.ops.business.development.domain.DevelopmentDataServiceRevision;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.repository.DevelopmentDataServiceDraftRepository;
import io.yak.ops.business.development.repository.DevelopmentDataServiceRevisionRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DevelopmentDataServiceNodeServiceTest {

  private DevelopmentNodeRepository nodeRepository;
  private TaskCatalogService taskCatalogService;
  private DevelopmentDataServiceDraftRepository draftRepository;
  private DevelopmentDataServiceRevisionRepository revisionRepository;
  private DevelopmentDataServiceSqlCompiler sqlCompiler;
  private DevelopmentDataServiceNodeService service;

  @BeforeEach
  void setUp() {
    nodeRepository = mock(DevelopmentNodeRepository.class);
    taskCatalogService = mock(TaskCatalogService.class);
    draftRepository = mock(DevelopmentDataServiceDraftRepository.class);
    revisionRepository = mock(DevelopmentDataServiceRevisionRepository.class);
    sqlCompiler = mock(DevelopmentDataServiceSqlCompiler.class);
    service = new DevelopmentDataServiceNodeService(
        nodeRepository,
        taskCatalogService,
        draftRepository,
        revisionRepository,
        mock(DataSourceExecutionProvider.class),
        sqlCompiler,
        new ObjectMapper());

    when(nodeRepository.findById(100L)).thenReturn(Optional.of(dataServiceNode(100L, 7L)));
  }

  @Test
  void saveDraftOwnsSqlAndDataSourceWithoutSqlAsset() {
    String sql = "select id, status from orders where status = :status";
    when(sqlCompiler.parameterNames(sql)).thenReturn(List.of("status"));

    DevelopmentDataServiceDefinition persisted = standaloneDefinition(42L, sql);
    DevelopmentDataServiceDraft saved = new DevelopmentDataServiceDraft(
        100L, persisted, 1L, Instant.EPOCH, Instant.EPOCH);
    when(draftRepository.save(eq(100L), org.mockito.ArgumentMatchers.any(), eq(0L)))
        .thenReturn(Optional.of(saved));
    when(revisionRepository.listByNodeId(100L)).thenReturn(List.of());

    DevelopmentDataServiceNodeService.DataServiceNodeContext context = service.saveDraft(
        100L,
        new DevelopmentDataServiceNodeService.SaveDraftCommand(
            42L,
            sql,
            "订单查询 API",
            "/orders",
            "GET",
            List.of(new DevelopmentDataServiceDefinition.ParameterContract(
                "status", "STRING", true, null, null)),
            List.of(),
            1000,
            30,
            null,
            true,
            true,
            0L));

    assertEquals(42L, context.draft().definition().dataSourceId());
    assertEquals(sql, context.draft().definition().sql());
    assertEquals(0L, context.draft().definition().sourceTaskAssetId());
    assertEquals(0L, context.draft().definition().sourceTaskRevisionId());
    assertTrue(context.draft().definition().paginationEnabled());
    assertTrue(context.draft().definition().autoParseParametersEnabled());
    verify(taskCatalogService, never()).get(anyLong());
    verify(nodeRepository).updateConfigured(100L, true);
  }

  @Test
  void publishCreatesStandaloneRevisionWithoutEnteringTaskCatalog() {
    String sql = "select id, status from orders where status = :status";
    DevelopmentDataServiceDefinition definition = standaloneDefinition(42L, sql);
    DevelopmentDataServiceDraft draft = new DevelopmentDataServiceDraft(
        100L, definition, 5L, Instant.EPOCH, Instant.EPOCH);
    when(draftRepository.findByNodeIdForUpdate(100L)).thenReturn(Optional.of(draft));
    when(sqlCompiler.parameterNames(sql)).thenReturn(List.of("status"));
    when(revisionRepository.findLatestByNodeId(100L)).thenReturn(Optional.empty());
    when(revisionRepository.nextRevisionNo(100L)).thenReturn(1);

    DevelopmentDataServiceRevision revision = new DevelopmentDataServiceRevision(
        900L, 100L, 1, 5L, definition, "checksum", Instant.EPOCH);
    when(revisionRepository.insert(
        eq(100L), eq(1), eq(5L), org.mockito.ArgumentMatchers.any(), anyString()))
        .thenReturn(revision);

    DevelopmentDataServiceRevision published = service.publish(100L, 5L);

    assertEquals(1, published.revisionNo());
    assertEquals(42L, published.definition().dataSourceId());
    assertEquals(sql, published.definition().sql());
    verify(taskCatalogService, never()).get(anyLong());
    verify(taskCatalogService, never()).publish(
        org.mockito.ArgumentMatchers.any(),
        anyString(),
        org.mockito.ArgumentMatchers.any(),
        anyString(),
        anyString(),
        anyLong(),
        org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void getHydratesHistoricalPinnedSqlIntoStandaloneSnapshot() {
    DevelopmentDataServiceDefinition legacy = legacyDefinition(300L, 401L, 3);
    DevelopmentDataServiceDraft draft = new DevelopmentDataServiceDraft(
        100L, legacy, 2L, Instant.EPOCH, Instant.EPOCH);
    TaskAsset asset = sqlAsset(300L, 200L, 7L, 401L, 3);

    when(draftRepository.findByNodeId(100L)).thenReturn(Optional.of(draft));
    when(revisionRepository.listByNodeId(100L)).thenReturn(List.of());
    when(taskCatalogService.get(300L)).thenReturn(asset);
    when(taskCatalogService.resolveRevision(300L, 401L))
        .thenReturn(sqlRevision(asset, 401L, 3));

    DevelopmentDataServiceNodeService.DataServiceNodeContext context = service.get(100L);

    assertEquals(42L, context.draft().definition().dataSourceId());
    assertEquals(
        "select id, status from orders where status = :status",
        context.draft().definition().sql());
    assertEquals(401L, context.draft().definition().sourceTaskRevisionId());
  }

  @Test
  void saveRejectsMissingDataSource() {
    IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> service.saveDraft(
            100L,
            new DevelopmentDataServiceNodeService.SaveDraftCommand(
                0L,
                "select 1",
                "订单查询 API",
                "/orders",
                "GET",
                List.of(),
                List.of(),
                1000,
                30,
                null,
                false,
                true,
                0L)));

    assertTrue(error.getMessage().contains("数据源"));
  }

  @Test
  void publishRejectsStaleDraftRevision() {
    DevelopmentDataServiceDraft draft = new DevelopmentDataServiceDraft(
        100L,
        standaloneDefinition(42L, "select id from orders"),
        6L,
        Instant.EPOCH,
        Instant.EPOCH);
    when(draftRepository.findByNodeIdForUpdate(100L)).thenReturn(Optional.of(draft));

    assertThrows(
        DevelopmentDraftConflictException.class,
        () -> service.publish(100L, 5L));
  }

  private static DevelopmentNode dataServiceNode(long id, long projectId) {
    return new DevelopmentNode(
        id, "订单查询 API", "DATA_SERVICE", projectId, null, false, Instant.EPOCH, Instant.EPOCH);
  }

  private static TaskAsset sqlAsset(
      long assetId,
      long sourceNodeId,
      long projectId,
      long currentRevisionId,
      int currentRevisionNo) {
    return new TaskAsset(
        assetId,
        TaskAssetSource.DATA_DEVELOPMENT,
        String.valueOf(sourceNodeId),
        projectId,
        "订单查询.sql",
        "SQL",
        TaskAssetStatus.ONLINE,
        new TaskRevisionRef(assetId, currentRevisionId, currentRevisionNo),
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private static TaskAssetRevision sqlRevision(TaskAsset asset, long revisionId, int revisionNo) {
    return new TaskAssetRevision(
        asset,
        new TaskSourceRevision(
            revisionId,
            revisionNo,
            new TaskDefinition(
                "SQL",
                1,
                "select id, status from orders where status = :status",
                "{\"dataSourceId\":\"42\",\"timeoutSeconds\":30}"),
            "sql-checksum"));
  }

  private static DevelopmentDataServiceDefinition standaloneDefinition(long dataSourceId, String sql) {
    return new DevelopmentDataServiceDefinition(
        0L,
        0L,
        0,
        "订单查询 API",
        "/orders",
        "GET",
        List.of(new DevelopmentDataServiceDefinition.ParameterContract(
            "status", "STRING", true, null, null)),
        List.of(new DevelopmentDataServiceDefinition.ResponseFieldContract(
            "id", "INTEGER", false, null, null)),
        1000,
        30,
        null,
        dataSourceId,
        sql);
  }

  private static DevelopmentDataServiceDefinition legacyDefinition(
      long assetId,
      long revisionId,
      int revisionNo) {
    return new DevelopmentDataServiceDefinition(
        assetId,
        revisionId,
        revisionNo,
        "订单查询 API",
        "/orders",
        "GET",
        List.of(new DevelopmentDataServiceDefinition.ParameterContract(
            "status", "STRING", true, null, null)),
        List.of(new DevelopmentDataServiceDefinition.ResponseFieldContract(
            "id", "INTEGER", false, null, null)),
        1000,
        30,
        null);
  }
}
