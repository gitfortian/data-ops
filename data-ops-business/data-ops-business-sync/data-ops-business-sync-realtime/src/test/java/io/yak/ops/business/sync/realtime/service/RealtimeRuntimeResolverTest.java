package io.yak.ops.business.sync.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RealtimeRuntimeResolverTest {

  private static final long JOB_ID = 7L;
  private static final long DEPLOYMENT_ID = 19L;
  private ComputeEnvironmentStore environments;
  private RealtimeJobStore jobs;
  private RealtimeRuntimeResolver resolver;

  @BeforeEach
  void setUp() {
    environments = mock(ComputeEnvironmentStore.class);
    jobs = mock(RealtimeJobStore.class);
    resolver = new RealtimeRuntimeResolver(environments, jobs);
  }

  @Test
  void deploymentUsesPersistedSnapshotEvenAfterEnvironmentChanges() {
    ComputeEnvironmentSnapshot deployed = snapshot(3L, "prod-a", "http://flink-a:8081", 2);
    when(jobs.deploymentEnvironment(DEPLOYMENT_ID)).thenReturn(Optional.of(deployed));
    when(environments.find(3L)).thenReturn(Optional.of(environment(3L, "prod-a", "http://flink-b:8081", 9, true)));

    ComputeEnvironmentSnapshot resolved = resolver.deployment(definition(3L), deployment(deployed));

    assertThat(resolved).isEqualTo(deployed);
    assertThat(resolved.config().restUrl()).isEqualTo("http://flink-a:8081");
    assertThat(resolved.version()).isEqualTo(2);
  }

  @Test
  void definitionRefusesDisabledEnvironmentForNewDeployment() {
    when(jobs.runtimeEnvironmentId(JOB_ID)).thenReturn(5L);
    when(environments.find(5L)).thenReturn(Optional.of(environment(5L, "disabled", "http://flink:8081", 1, false)));

    assertThatThrownBy(() -> resolver.definition(definition(5L), true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("已停用");
  }

  @Test
  void deploymentRejectsMissingRuntimeSnapshot() {
    when(jobs.deploymentEnvironment(DEPLOYMENT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> resolver.deployment(definition(3L), deployment(null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("缺少运行环境快照");
  }

  private ComputeEnvironment environment(long id, String name, String restUrl, int version, boolean enabled) {
    LocalDateTime now = LocalDateTime.now();
    return new ComputeEnvironment(id, name, ComputeEnvironment.ENGINE_FLINK_CDC, ComputeEnvironment.DEPLOYMENT_REMOTE, ComputeEnvironment.SUBMITTER_LOCAL, new RuntimeConfig(restUrl, "/opt/flink", "/opt/flink-cdc", null, "1.20.5", "3.6.0"), enabled, false, version, now, now);
  }

  private ComputeEnvironmentSnapshot snapshot(long id, String name, String restUrl, int version) {
    return new ComputeEnvironmentSnapshot(id, name, ComputeEnvironment.ENGINE_FLINK_CDC, ComputeEnvironment.DEPLOYMENT_REMOTE, ComputeEnvironment.SUBMITTER_LOCAL, new RuntimeConfig(restUrl, "/opt/flink", "/opt/flink-cdc", null, "1.20.5", "3.6.0"), version);
  }

  private DefinitionRow definition(long environmentId) {
    return new DefinitionRow(JOB_ID, "test-job", null, null, environmentId, "PUBLISHED", "RUNNING", "RUNNING", 1, 1, "digest", null, null, null);
  }

  private DeploymentRow deployment(ComputeEnvironmentSnapshot snapshot) {
    return new DeploymentRow(DEPLOYMENT_ID, JOB_ID, 1, null, "summary", "digest", "key", "0123456789abcdef0123456789abcdef", "flink-cdc-cli-3.6.0@env-3-v2", snapshot, "RUNNING", false, null, null, null);
  }
}
