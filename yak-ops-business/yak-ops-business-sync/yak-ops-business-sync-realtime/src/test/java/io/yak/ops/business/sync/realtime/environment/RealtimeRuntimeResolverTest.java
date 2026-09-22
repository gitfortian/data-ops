package io.yak.ops.business.sync.realtime.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RealtimeRuntimeResolverTest {

  @Test
  void disabledEnvironmentCannotBeUsedForNewWork() {
    ComputeEnvironmentStore environments = mock(ComputeEnvironmentStore.class);
    RealtimeJobStore jobs = mock(RealtimeJobStore.class);
    RealtimeRuntimeResolver resolver = new RealtimeRuntimeResolver(environments, jobs);
    when(environments.find(3L))
        .thenReturn(Optional.of(environment(false, 5, "http://flink:8081")));

    assertThatThrownBy(() -> resolver.environment(3L, true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("运行环境已停用");
  }

  @Test
  void existingExecutionUsesItsFrozenSnapshotWithoutReadingCurrentEnvironment() {
    ComputeEnvironmentStore environments = mock(ComputeEnvironmentStore.class);
    RealtimeJobStore jobs = mock(RealtimeJobStore.class);
    RealtimeRuntimeResolver resolver = new RealtimeRuntimeResolver(environments, jobs);
    ComputeEnvironmentSnapshot frozen =
        ComputeEnvironmentSnapshot.from(environment(true, 2, "http://flink-old:8081"));
    DefinitionRow task = definition();
    DeploymentRow execution = deployment(frozen);

    ComputeEnvironmentSnapshot resolved = resolver.deployment(task, execution);

    assertThat(resolved).isSameAs(frozen);
    assertThat(resolved.config().restUrl()).isEqualTo("http://flink-old:8081");
    verify(jobs, never()).deploymentEnvironment(19L);
    verify(environments, never()).find(3L);
  }

  @Test
  void unhydratedExecutionUsesPersistedDeploymentSnapshotNotCurrentEnvironment() {
    ComputeEnvironmentStore environments = mock(ComputeEnvironmentStore.class);
    RealtimeJobStore jobs = mock(RealtimeJobStore.class);
    RealtimeRuntimeResolver resolver = new RealtimeRuntimeResolver(environments, jobs);
    ComputeEnvironmentSnapshot persisted =
        ComputeEnvironmentSnapshot.from(environment(true, 2, "http://flink-old:8081"));
    DefinitionRow task = definition();
    DeploymentRow execution = deployment(null);
    when(jobs.deploymentEnvironment(19L)).thenReturn(Optional.of(persisted));

    ComputeEnvironmentSnapshot resolved = resolver.deployment(task, execution);

    assertThat(resolved).isSameAs(persisted);
    assertThat(resolved.config().restUrl()).isEqualTo("http://flink-old:8081");
    verify(environments, never()).find(3L);
  }

  private DefinitionRow definition() {
    LocalDateTime now = LocalDateTime.now();
    return new DefinitionRow(
        7L,
        "orders",
        null,
        null,
        3L,
        "PUBLISHED",
        "RUNNING",
        "RUNNING",
        3,
        3,
        "digest",
        null,
        now,
        now);
  }

  private DeploymentRow deployment(ComputeEnvironmentSnapshot snapshot) {
    LocalDateTime now = LocalDateTime.now();
    return new DeploymentRow(
        19L,
        7L,
        31L,
        3,
        null,
        "summary",
        "artifact",
        "key",
        "job-1",
        snapshot == null ? null : snapshot.runtimeRevision(),
        snapshot,
        "RUNNING",
        false,
        null,
        now,
        now);
  }

  private ComputeEnvironment environment(boolean enabled, int version, String restUrl) {
    LocalDateTime now = LocalDateTime.now();
    return new ComputeEnvironment(
        3L,
        "runtime-env",
        ComputeEnvironment.ENGINE_FLINK_CDC,
        ComputeEnvironment.DEPLOYMENT_REMOTE,
        ComputeEnvironment.SUBMITTER_LOCAL,
        new RuntimeConfig(restUrl, "/opt/flink", "/opt/flink-cdc", null, "1.20.5", "3.6.0"),
        enabled,
        false,
        version,
        now,
        now);
  }
}
