package io.yak.ops.business.sync.realtime.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentDiagnosis;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.engine.FlinkRuntimeEnvironmentProbe;
import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComputeEnvironmentDiagnoserTest {

  @Test
  void persistsDiagnosisSummaryForSavedEnvironment() {
    ComputeEnvironmentStore store = mock(ComputeEnvironmentStore.class);
    FlinkRuntimeEnvironmentProbe probe = mock(FlinkRuntimeEnvironmentProbe.class);
    ComputeEnvironmentManager manager = mock(ComputeEnvironmentManager.class);
    ComputeEnvironmentDiagnoser diagnoser =
        new ComputeEnvironmentDiagnoser(
            manager, store, new ComputeEnvironmentConfigNormalizer(), probe);

    LocalDateTime checkedAt = LocalDateTime.of(2026, 8, 22, 10, 0);
    ComputeEnvironment environment =
        new ComputeEnvironment(
            11L,
            "生产实时环境",
            ComputeEnvironment.ENGINE_FLINK_CDC,
            ComputeEnvironment.DEPLOYMENT_REMOTE,
            ComputeEnvironment.SUBMITTER_LOCAL,
            new RuntimeConfig(
                "http://flink:8081",
                "/opt/flink",
                "/opt/flink-cdc",
                null,
                "1.20.5",
                "3.6.0"),
            true,
            false,
            3,
            checkedAt.minusDays(1),
            checkedAt.minusDays(1));
    ComputeEnvironmentDiagnosis diagnosis =
        new ComputeEnvironmentDiagnosis(
            11L,
            environment.name(),
            ComputeEnvironmentDiagnosis.STATUS_HEALTHY,
            true,
            "环境检查通过，可以提交实时同步任务",
            "1.20.5",
            "3.6.0",
            "17.0.12",
            checkedAt,
            List.of());
    when(manager.require(11L)).thenReturn(environment);
    when(probe.diagnose(any(ComputeEnvironmentSnapshot.class))).thenReturn(diagnosis);

    ComputeEnvironmentDiagnosis result = diagnoser.diagnose(11L);

    assertThat(result).isEqualTo(diagnosis);
    verify(store)
        .saveDiagnosis(
            11L,
            ComputeEnvironmentDiagnosis.STATUS_HEALTHY,
            "环境检查通过，可以提交实时同步任务",
            checkedAt);
  }
}
