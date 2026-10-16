package io.yak.ops.business.sync.offline.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.sync.offline.dao.mapper.OfflineOdsAppendFenceMapper;
import io.yak.ops.business.sync.offline.dao.model.OfflineOdsAppendFencePO;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class OfflineOdsAppendFenceRepositoryTest {
  private final OfflineOdsAppendFenceMapper mapper = mock(OfflineOdsAppendFenceMapper.class);
  private final CurrentProject project = mock(CurrentProject.class);
  private final OfflineOdsAppendFenceRepository repo =
      new OfflineOdsAppendFenceRepository(mapper, project);

  private static final String FROZEN = """
      {"odsHandoff":{"modelId":50,"versionNo":2,"targetObservationId":99},
       "sink":{"dataSourceId":"20","config":{"table":"ods.ods_order_detail"}}}
      """;

  @Test void reservesProjectDestinationAndFrozenBatchScope() {
    when(project.requireProjectId()).thenReturn(23L);
    when(mapper.insert(any(OfflineOdsAppendFencePO.class))).thenReturn(1);
    repo.claim(FROZEN, 501L, BatchScope.fullSelection());

    var saved = ArgumentCaptor.forClass(OfflineOdsAppendFencePO.class);
    verify(mapper).insert(saved.capture());
    assertEquals(23L, saved.getValue().getProjectId());
    assertEquals(20L, saved.getValue().getTargetDatasourceId());
    assertEquals("ods_order_detail", saved.getValue().getTargetTableKey());
    assertEquals(501L, saved.getValue().getJobDefinitionId());
    assertEquals(50L, saved.getValue().getModelId());
    assertEquals(99L, saved.getValue().getTargetObservationId());
    assertEquals(BatchScope.fullSelection().fingerprint(), saved.getValue().getScopeSha256());
  }

  @Test void noDatabaseOrSchemaAliasCanBypassConservativeTargetKey() {
    assertEquals("ods_order_detail",
        OfflineOdsAppendFenceRepository.normalizeTableKey("ods_order_detail"));
    assertEquals("ods_order_detail",
        OfflineOdsAppendFenceRepository.normalizeTableKey("ods.ODS_Order_Detail"));
    assertEquals("ods_order_detail",
        OfflineOdsAppendFenceRepository.normalizeTableKey("warehouse.ods.ods_order_detail"));
    assertThrows(IllegalArgumentException.class,
        () -> OfflineOdsAppendFenceRepository.normalizeTableKey("foo;DROP TABLE"));
    assertThrows(IllegalArgumentException.class,
        () -> OfflineOdsAppendFenceRepository.normalizeTableKey("db..table"));
  }

  @Test void duplicateDatabaseFenceFailsClosedWithNoImplicitRelease() {
    when(project.requireProjectId()).thenReturn(23L);
    when(mapper.insert(any(OfflineOdsAppendFencePO.class)))
        .thenThrow(new DuplicateKeyException("same destination"));
    var err = assertThrows(IllegalStateException.class,
        () -> repo.claim(FROZEN, 777L, BatchScope.fullSelection()));
    assertTrue(err.getMessage().contains("跨任务或跨 Batch"));
    verify(mapper).insert(any(OfflineOdsAppendFencePO.class));
  }

  @Test void malformedFrozenTargetCannotWriteReservation() {
    String bad = FROZEN.replace("ods.ods_order_detail", "ods..ods_order_detail");
    assertThrows(IllegalArgumentException.class,
        () -> repo.claim(bad, 501L, BatchScope.fullSelection()));
    verifyNoInteractions(mapper);
  }
}
