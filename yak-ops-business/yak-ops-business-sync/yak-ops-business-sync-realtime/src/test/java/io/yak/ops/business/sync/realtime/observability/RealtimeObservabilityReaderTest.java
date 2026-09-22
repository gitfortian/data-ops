package io.yak.ops.business.sync.realtime.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.engine.FlinkObservabilityClient;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.business.sync.realtime.service.RealtimeRuntimeResolver;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RealtimeObservabilityReaderTest {

  private static final long JOB_ID = 7L;
  private RealtimeJobStore store;
  private FlinkObservabilityClient flink;
  private RealtimeRuntimeResolver runtimeResolver;
  private RealtimeObservabilityReader reader;
  private ComputeEnvironmentSnapshot environment;

  @BeforeEach
  void setUp() {
    store = mock(RealtimeJobStore.class);
    flink = mock(FlinkObservabilityClient.class);
    runtimeResolver = mock(RealtimeRuntimeResolver.class);
    environment =
        new ComputeEnvironmentSnapshot(
            3L,
            "test-env",
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
    when(runtimeResolver.deployment(any(), any())).thenReturn(environment);
    reader = new RealtimeObservabilityReader(store, runtimeResolver, flink);
    when(store.definition(JOB_ID)).thenReturn(Optional.of(definition()));
  }

  @Test
  void readsSubmissionLogBeforeFlinkJobIdIsRecovered() {
    when(store.latestDeployment(JOB_ID)).thenReturn(Optional.of(deployment(null)));
    when(flink.submissionLog("start-key", 500)).thenReturn("submitting");

    assertThat(reader.submissionLog(JOB_ID, 500)).isEqualTo("submitting");

    verify(flink).submissionLog("start-key", 500);
  }

  @Test
  void runtimeObservabilityStillRequiresFlinkJobId() {
    when(store.latestDeployment(JOB_ID)).thenReturn(Optional.of(deployment(null)));

    assertThatThrownBy(() -> reader.runtimeLog(JOB_ID, 50))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("尚无 Flink jobId");
  }

  private DefinitionRow definition() {
    LocalDateTime now = LocalDateTime.now();
    return new DefinitionRow(
        JOB_ID,
        "test-job",
        null,
        null,
        environment.id(),
        "PUBLISHED",
        "RUNNING",
        "STARTING",
        1,
        1,
        "digest",
        null,
        now,
        now);
  }

  private DeploymentRow deployment(String engineJobId) {
    LocalDateTime now = LocalDateTime.now();
    return new DeploymentRow(
        19L,
        JOB_ID,
        1,
        null,
        "summary",
        "digest",
        "start-key",
        engineJobId,
        null,
        environment,
        "SUBMITTING",
        true,
        null,
        now,
        now);
  }
}
