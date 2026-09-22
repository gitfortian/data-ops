package io.yak.ops.business.sync.realtime.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.dao.RealtimeJobDao;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDefinitionPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDeploymentPO;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.repository.support.RealtimeJsonCodec;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class RealtimeJobStoreAdapterExecutionProjectionTest {

  @Test
  void detailProjectionUsesLatestExecutionStateInsteadOfStaleTaskRuntimeProjection() {
    Fixture fixture = fixture();
    fixture.task.setDesiredState("RUNNING");
    fixture.task.setObservedState("UNKNOWN");
    fixture.task.setLastError("stale task projection error");
    fixture.execution.setDesiredState("STOPPED");
    fixture.execution.setObservedState("STOPPED");
    fixture.execution.setStatus("STOPPED");
    fixture.execution.setErrorMessage(null);

    RealtimeJobView view = fixture.store.view(7L);

    assertThat(view.desiredState()).isEqualTo("STOPPED");
    assertThat(view.observedState()).isEqualTo("STOPPED");
    assertThat(view.lastError()).isNull();
    assertThat(view.releaseState()).isEqualTo("DRAFT");
    assertThat(view.latestDeployment().runtimeEnvironment().id()).isEqualTo(3L);
    assertThat(view.publishedUpdateAvailable()).isFalse();
  }

  @Test
  void taskWithoutExecutionDerivesStoppedAndIgnoresLegacyRuntimeColumns() {
    Fixture fixture = fixture();
    fixture.task.setDesiredState("RUNNING");
    fixture.task.setObservedState("UNKNOWN");
    fixture.task.setLastError("legacy projection must not leak");
    when(fixture.dao.latestDeployment(7L)).thenReturn(Optional.empty());

    RealtimeJobView view = fixture.store.view(7L);

    assertThat(view.desiredState()).isEqualTo("STOPPED");
    assertThat(view.observedState()).isEqualTo("STOPPED");
    assertThat(view.lastError()).isNull();
    assertThat(view.latestDeployment()).isNull();
  }

  @Test
  void publishedUpdateAvailableUsesImmutableVersionIdsNotLegacyRevisions() {
    Fixture fixture = fixture();
    fixture.task.setPublishedDefinitionVersionId(32L);
    fixture.task.setPublishedVersion(4);
    fixture.execution.setDefinitionVersionId(31L);
    // Deliberately make the legacy revision numbers equal. They must not decide this capability.
    fixture.execution.setDefinitionVersion(4);
    fixture.execution.setDesiredState("RUNNING");
    fixture.execution.setObservedState("RUNNING");
    fixture.execution.setStatus("RUNNING");

    RealtimeJobView view = fixture.store.view(7L);

    assertThat(view.publishedUpdateAvailable()).isTrue();
  }

  private Fixture fixture() {
    RealtimeJobDao dao = mock(RealtimeJobDao.class);
    RealtimeJsonCodec json = mock(RealtimeJsonCodec.class);
    RealtimeJobListQuery listQuery = mock(RealtimeJobListQuery.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    RealtimeJobStoreAdapter store = new RealtimeJobStoreAdapter(dao, json, listQuery, events);

    RealtimeJobDefinitionPO task = new RealtimeJobDefinitionPO();
    task.setId(7L);
    task.setJobName("orders-sync");
    task.setRuntimeEnvironmentId(4L);
    task.setReleaseState("DRAFT");
    task.setDesiredState("STOPPED");
    task.setObservedState("STOPPED");
    task.setDefinitionVersion(4);
    task.setPublishedVersion(3);
    task.setPublishedDefinitionVersionId(31L);
    task.setCreateTime(LocalDateTime.now());
    task.setUpdateTime(LocalDateTime.now());

    RealtimeJobDeploymentPO execution = new RealtimeJobDeploymentPO();
    execution.setId(19L);
    execution.setDefinitionId(7L);
    execution.setDefinitionVersionId(31L);
    execution.setDefinitionVersion(3);
    execution.setRuntimeEnvironmentId(3L);
    execution.setRuntimeEnvironmentVersion(2);
    execution.setRuntimeEnvironmentSnapshotJson("{}");
    execution.setSpecSnapshotJson("{}");
    execution.setConfigDigest("a".repeat(64));
    execution.setIdempotencyKey("exec-19");
    execution.setEngineType("FLINK_CDC");
    execution.setDesiredState("STOPPED");
    execution.setObservedState("STOPPED");
    execution.setStatus("STOPPED");
    execution.setResultUncertain(false);
    execution.setErrorMessage(null);
    execution.setCreateTime(LocalDateTime.now());
    execution.setUpdateTime(LocalDateTime.now());

    ComputeEnvironmentSnapshot runtime =
        new ComputeEnvironmentSnapshot(
            3L,
            "published-env",
            ComputeEnvironment.ENGINE_FLINK_CDC,
            ComputeEnvironment.DEPLOYMENT_REMOTE,
            ComputeEnvironment.SUBMITTER_LOCAL,
            new RuntimeConfig(
                "http://127.0.0.1:8081",
                "/opt/flink",
                "/opt/flink-cdc",
                null,
                "1.20.5",
                "3.6.0"),
            2);

    when(dao.findDefinition(7L)).thenReturn(Optional.of(task));
    when(dao.latestDeployment(7L)).thenReturn(Optional.of(execution));
    when(json.readEnvironmentSnapshot("{}")).thenReturn(runtime);
    return new Fixture(store, dao, task, execution);
  }

  private record Fixture(
      RealtimeJobStoreAdapter store,
      RealtimeJobDao dao,
      RealtimeJobDefinitionPO task,
      RealtimeJobDeploymentPO execution) {}
}
