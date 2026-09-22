package io.yak.ops.business.sync.offline.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.engine.connector.OfflineSyncConnectorRuntime;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionLogQuery;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionQuery;
import io.yak.ops.business.sync.offline.mapping.OfflineSyncViewMapper;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionVO;
import org.junit.jupiter.api.Test;

class OfflineJobExecutionServiceEntryPointTest {

  @Test
  void occupancyQueryDelegatesToBatchRuntimeTruth() {
    Fixture fixture = fixture();
    when(fixture.runtime.hasOccupyingBatch(10L)).thenReturn(true);

    assertThat(fixture.service.hasOccupyingBatch(10L)).isTrue();

    verify(fixture.runtime).hasOccupyingBatch(10L);
  }

  @Test
  void scheduledExecutionPreservesScheduleTriggerToken() {
    Fixture fixture = fixture();
    String triggerToken = "SCHEDULE|schedule-10|2026-08-23T08:00:00";
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(21L);
    OfflineJobExecutionVO view = mock(OfflineJobExecutionVO.class);

    when(fixture.coordinator.execute(10L, triggerToken, null, 1)).thenReturn(execution);
    when(fixture.executionQuery.toVO(execution)).thenReturn(view);

    assertThat(fixture.service.executeScheduled(10L, triggerToken)).isSameAs(view);

    verify(fixture.coordinator).execute(10L, triggerToken, null, 1);
    verify(fixture.executionQuery).toVO(execution);
  }

  @Test
  void scheduleGatewayReturnsOnlyExecutionIdentity() {
    Fixture fixture = fixture();
    String triggerToken = "SCHEDULE|schedule-10|2026-08-23T08:00:00";
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(21L);
    OfflineJobExecutionVO view = mock(OfflineJobExecutionVO.class);
    when(view.getId()).thenReturn(21L);
    when(fixture.coordinator.execute(10L, triggerToken, null, 1)).thenReturn(execution);
    when(fixture.executionQuery.toVO(execution)).thenReturn(view);

    assertThat(fixture.service.submitScheduled(10L, triggerToken)).isEqualTo(21L);

    verify(fixture.coordinator).execute(10L, triggerToken, null, 1);
    verify(view).getId();
  }

  @Test
  void pendingBackfillExecutionDelegatesToCoordinator() {
    Fixture fixture = fixture();
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(31L);
    OfflineJobExecutionVO view = mock(OfflineJobExecutionVO.class);

    when(fixture.coordinator.executePendingBackfill(77L)).thenReturn(execution);
    when(fixture.executionQuery.toVO(execution)).thenReturn(view);

    assertThat(fixture.service.executePendingBackfill(77L)).isSameAs(view);

    verify(fixture.coordinator).executePendingBackfill(77L);
    verify(fixture.executionQuery).toVO(execution);
  }

  private Fixture fixture() {
    OfflineExecutionCoordinator coordinator = mock(OfflineExecutionCoordinator.class);
    OfflineBatchRuntime runtime = mock(OfflineBatchRuntime.class);
    OfflineExecutionQuery executionQuery = mock(OfflineExecutionQuery.class);
    OfflineExecutionLogQuery executionLogQuery = mock(OfflineExecutionLogQuery.class);
    LinkUpClient linkUpClient = mock(LinkUpClient.class);
    OfflineSyncViewMapper viewMapper = mock(OfflineSyncViewMapper.class);
    OfflineSyncConnectorRuntime connectorRuntime = mock(OfflineSyncConnectorRuntime.class);
    return new Fixture(
        new OfflineJobExecutionService(
            coordinator,
            runtime,
            executionQuery,
            executionLogQuery,
            linkUpClient,
            viewMapper,
            connectorRuntime),
        coordinator,
        runtime,
        executionQuery);
  }

  private record Fixture(
      OfflineJobExecutionService service,
      OfflineExecutionCoordinator coordinator,
      OfflineBatchRuntime runtime,
      OfflineExecutionQuery executionQuery) {}
}
