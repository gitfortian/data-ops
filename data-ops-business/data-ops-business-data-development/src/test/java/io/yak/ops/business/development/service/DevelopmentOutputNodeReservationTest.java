package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.repository.DevelopmentDirectoryRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Phase 2 intentionally lifts the Phase-1 output-node reservation. */
class DevelopmentOutputNodeReservationTest {

  @Test
  void outputNodeTypesBecomeCreatableWithoutEnteringTaskCatalogLifecycle() {
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    DevelopmentDirectoryRepository directories = mock(DevelopmentDirectoryRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DevelopmentNodeService service = new DevelopmentNodeService(nodes, directories, catalog);

    when(nodes.existsByName(any(), any())).thenReturn(false);
    when(nodes.insert(any(), any(), any(), any(), anyBoolean())).thenAnswer(invocation ->
        node(10L,
            invocation.getArgument(0),
            invocation.getArgument(1),
            invocation.getArgument(2)));

    DevelopmentNode dataset = service.create("订单数据集", "dataset", 7L, null);
    DevelopmentNode dataService = service.create("订单查询 API", "data_service", 7L, null);

    assertEquals("DATASET", dataset.type());
    assertEquals("DATA_SERVICE", dataService.type());

    when(nodes.findById(10L)).thenReturn(Optional.of(dataset));
    when(nodes.deleteById(10L)).thenReturn(true);
    service.delete(10L);
    verify(catalog, never()).offlineSource(eq(TaskAssetSource.DATA_DEVELOPMENT), any());
  }

  private DevelopmentNode node(Long id, String name, String type, Long projectId) {
    Instant now = Instant.now();
    return new DevelopmentNode(
        id, name, type, projectId, null, false, now, now, "tester", false);
  }
}
