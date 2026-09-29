package io.yak.ops.business.development.dataservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.ResolvedSource;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDefinition;
import io.yak.ops.business.development.domain.DevelopmentDataServiceRevision;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.repository.DevelopmentDataServiceRevisionRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DevelopmentDataServiceNodeSourceProviderTest {

  private DevelopmentNodeRepository nodeRepository;
  private DevelopmentDataServiceRevisionRepository revisionRepository;
  private TaskCatalogService taskCatalogService;
  private DevelopmentDataServiceNodeSourceProvider provider;

  @BeforeEach
  void setUp() {
    nodeRepository = mock(DevelopmentNodeRepository.class);
    revisionRepository = mock(DevelopmentDataServiceRevisionRepository.class);
    taskCatalogService = mock(TaskCatalogService.class);
    provider = new DevelopmentDataServiceNodeSourceProvider(
        nodeRepository, revisionRepository, taskCatalogService, new ObjectMapper());
  }

  @Test
  void resolveUsesSqlOwnedByPublishedDataServiceRevision() {
    DevelopmentNode node = dataServiceNode();
    DevelopmentDataServiceRevision dsRevision = standaloneRevision(900L, 2);

    when(nodeRepository.findById(100L)).thenReturn(Optional.of(node));
    when(revisionRepository.findLatestByNodeId(100L)).thenReturn(Optional.of(dsRevision));

    ResolvedSource resolved = provider.resolve("100");

    assertThat(provider.managesServiceDefinition()).isTrue();
    assertThat(resolved.descriptor().sourceType())
        .isEqualTo(DevelopmentDataServiceNodeSourceProvider.SOURCE_TYPE);
    assertThat(resolved.descriptor().sourceRef()).isEqualTo("100");
    assertThat(resolved.descriptor().sourceRevisionId()).isEqualTo(900L);
    assertThat(resolved.descriptor().sourceRevisionNo()).isEqualTo(2);
    assertThat(resolved.descriptor().name()).isEqualTo("订单查询 API");
    assertThat(resolved.descriptor().defaultPath()).isEqualTo("/orders");
    assertThat(resolved.descriptor().maxRows()).isEqualTo(500);
    assertThat(resolved.descriptor().timeoutSeconds()).isEqualTo(20);
    assertThat(resolved.descriptor().dataSourceId()).isEqualTo(42L);
    assertThat(resolved.sql())
        .isEqualTo("select id, status from orders where status = :status");
    assertThat(resolved.contract().parameters()).hasSize(1);
    assertThat(resolved.contract().responseFields()).hasSize(1);
    verify(taskCatalogService, never()).get(org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void listOnlyExposesDataServiceNodesWithPublishedRevision() {
    DevelopmentNode published = dataServiceNode();
    DevelopmentNode unpublished = new DevelopmentNode(
        101L, "未发布 API", "DATA_SERVICE", 7L, null, true, Instant.EPOCH, Instant.EPOCH);
    DevelopmentNode sql = new DevelopmentNode(
        200L, "orders.sql", "SQL", 7L, null, true, Instant.EPOCH, Instant.EPOCH);
    DevelopmentDataServiceRevision dsRevision = standaloneRevision(900L, 2);

    when(nodeRepository.list()).thenReturn(List.of(published, unpublished, sql));
    when(revisionRepository.findLatestByNodeId(100L)).thenReturn(Optional.of(dsRevision));
    when(revisionRepository.findLatestByNodeId(101L)).thenReturn(Optional.empty());
    when(nodeRepository.findById(100L)).thenReturn(Optional.of(published));

    var page = provider.list(1, 20, "订单");

    assertThat(page.total()).isEqualTo(1L);
    assertThat(page.records()).hasSize(1);
    assertThat(page.records().getFirst().sourceRef()).isEqualTo("100");
  }

  @Test
  void resolveKeepsHistoricalPinnedSqlRevisionCompatible() {
    DevelopmentNode node = dataServiceNode();
    DevelopmentDataServiceRevision legacyRevision = legacyRevision(900L, 1, 401L, 3);
    TaskAsset sqlAsset = sqlAsset(TaskAssetStatus.OFFLINE, 402L, 4);

    when(nodeRepository.findById(100L)).thenReturn(Optional.of(node));
    when(revisionRepository.findLatestByNodeId(100L)).thenReturn(Optional.of(legacyRevision));
    when(taskCatalogService.get(300L)).thenReturn(sqlAsset);
    when(taskCatalogService.resolveRevision(300L, 401L))
        .thenReturn(sqlRevision(sqlAsset, 401L, 3));

    ResolvedSource resolved = provider.resolve("100");

    assertThat(resolved.descriptor().dataSourceId()).isEqualTo(42L);
    assertThat(resolved.sql())
        .isEqualTo("select id, status from orders where status = :status");
  }

  private static DevelopmentNode dataServiceNode() {
    return new DevelopmentNode(
        100L, "订单查询 API", "DATA_SERVICE", 7L, null, true, Instant.EPOCH, Instant.EPOCH);
  }

  private static DevelopmentDataServiceRevision standaloneRevision(long revisionId, int revisionNo) {
    DevelopmentDataServiceDefinition definition = new DevelopmentDataServiceDefinition(
        0L,
        0L,
        0,
        "订单查询 API",
        "/orders",
        "GET",
        List.of(new DevelopmentDataServiceDefinition.ParameterContract(
            "status", "STRING", true, "订单状态", "PAID")),
        List.of(new DevelopmentDataServiceDefinition.ResponseFieldContract(
            "id", "INTEGER", false, "订单 ID", "1")),
        500,
        20,
        "供运营系统查询",
        42L,
        "select id, status from orders where status = :status");
    return new DevelopmentDataServiceRevision(
        revisionId, 100L, revisionNo, 5L, definition, "ds-checksum", Instant.EPOCH);
  }

  private static DevelopmentDataServiceRevision legacyRevision(
      long revisionId,
      int revisionNo,
      long sqlRevisionId,
      int sqlRevisionNo) {
    DevelopmentDataServiceDefinition definition = new DevelopmentDataServiceDefinition(
        300L,
        sqlRevisionId,
        sqlRevisionNo,
        "订单查询 API",
        "/orders",
        "GET",
        List.of(new DevelopmentDataServiceDefinition.ParameterContract(
            "status", "STRING", true, "订单状态", "PAID")),
        List.of(new DevelopmentDataServiceDefinition.ResponseFieldContract(
            "id", "INTEGER", false, "订单 ID", "1")),
        500,
        20,
        "供运营系统查询");
    return new DevelopmentDataServiceRevision(
        revisionId, 100L, revisionNo, 5L, definition, "legacy-checksum", Instant.EPOCH);
  }

  private static TaskAsset sqlAsset(
      TaskAssetStatus status,
      long currentRevisionId,
      int currentRevisionNo) {
    return new TaskAsset(
        300L,
        TaskAssetSource.DATA_DEVELOPMENT,
        "200",
        7L,
        "orders.sql",
        "SQL",
        status,
        new TaskRevisionRef(300L, currentRevisionId, currentRevisionNo),
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
}
