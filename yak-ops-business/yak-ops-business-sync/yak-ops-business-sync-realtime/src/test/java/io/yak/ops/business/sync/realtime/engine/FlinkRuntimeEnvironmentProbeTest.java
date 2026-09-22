package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentDiagnosis;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlinkRuntimeEnvironmentProbeTest {

  @TempDir Path temp;

  @Test
  void detectsLocalFlinkCdcJavaAndWritableWorkDirectory() throws Exception {
    Path flinkHome = Files.createDirectories(temp.resolve("flink/bin")).getParent();
    Path cdcHome = Files.createDirectories(temp.resolve("flink-cdc/bin")).getParent();
    Path javaHome = Files.createDirectories(temp.resolve("java/bin")).getParent();

    executable(flinkHome.resolve("bin/flink"), "#!/bin/sh\necho 'Version: 1.20.5'\n");
    executable(cdcHome.resolve("bin/flink-cdc.sh"), "#!/bin/sh\necho 'Flink CDC version 3.6.0'\n");
    executable(javaHome.resolve("bin/java"), "#!/bin/sh\necho 'openjdk version \"17.0.12\"' >&2\n");

    RealtimeEngineGateway gateway = mock(RealtimeEngineGateway.class);
    ObjectNode overview = new ObjectMapper().createObjectNode();
    overview.put("flink-version", "1.20.5");
    overview.put("taskmanagers", 2);
    overview.put("slots-total", 8);
    overview.put("slots-available", 6);
    overview.put("jobs-running", 1);
    when(gateway.health(org.mockito.ArgumentMatchers.any())).thenReturn(overview);

    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    properties.setWorkDirectory(temp.resolve("work").toString());
    FlinkRuntimeEnvironmentProbe probe = new FlinkRuntimeEnvironmentProbe(gateway, properties);
    ComputeEnvironmentSnapshot environment =
        new ComputeEnvironmentSnapshot(
            3L,
            "local",
            ComputeEnvironment.ENGINE_FLINK_CDC,
            ComputeEnvironment.DEPLOYMENT_REMOTE,
            ComputeEnvironment.SUBMITTER_LOCAL,
            new RuntimeConfig(
                "http://flink:8081",
                flinkHome.toString(),
                cdcHome.toString(),
                javaHome.toString(),
                "1.20.5",
                "3.6.0"),
            2);

    ComputeEnvironmentDiagnosis result = probe.diagnose(environment);

    assertThat(result.status()).isEqualTo(ComputeEnvironmentDiagnosis.STATUS_HEALTHY);
    assertThat(result.ready()).isTrue();
    assertThat(result.detectedFlinkVersion()).isEqualTo("1.20.5");
    assertThat(result.detectedFlinkCdcVersion()).isEqualTo("3.6.0");
    assertThat(result.detectedJavaVersion()).isEqualTo("17.0.12");
    assertThat(result.checks())
        .allMatch(check -> ComputeEnvironmentDiagnosis.Check.PASS.equals(check.status()));
  }

  @Test
  void reportsConfiguredVersionMismatchAsWarningNotFailure() throws Exception {
    Path flinkHome = Files.createDirectories(temp.resolve("flink2/bin")).getParent();
    Path cdcHome = Files.createDirectories(temp.resolve("flink-cdc2/bin")).getParent();
    Path javaHome = Files.createDirectories(temp.resolve("java2/bin")).getParent();

    executable(flinkHome.resolve("bin/flink"), "#!/bin/sh\necho 'Version: 1.20.5'\n");
    executable(cdcHome.resolve("bin/flink-cdc.sh"), "#!/bin/sh\necho 'Flink CDC version 3.6.0'\n");
    executable(javaHome.resolve("bin/java"), "#!/bin/sh\necho 'openjdk version \"17.0.12\"' >&2\n");

    RealtimeEngineGateway gateway = mock(RealtimeEngineGateway.class);
    ObjectNode overview = new ObjectMapper().createObjectNode();
    overview.put("flink-version", "1.20.5");
    when(gateway.health(org.mockito.ArgumentMatchers.any())).thenReturn(overview);

    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    properties.setWorkDirectory(temp.resolve("work2").toString());
    FlinkRuntimeEnvironmentProbe probe = new FlinkRuntimeEnvironmentProbe(gateway, properties);
    ComputeEnvironmentSnapshot environment =
        new ComputeEnvironmentSnapshot(
            4L,
            "mismatch",
            ComputeEnvironment.ENGINE_FLINK_CDC,
            ComputeEnvironment.DEPLOYMENT_REMOTE,
            ComputeEnvironment.SUBMITTER_LOCAL,
            new RuntimeConfig(
                "http://flink:8081",
                flinkHome.toString(),
                cdcHome.toString(),
                javaHome.toString(),
                "1.19.0",
                "3.5.0"),
            1);

    ComputeEnvironmentDiagnosis result = probe.diagnose(environment);

    assertThat(result.status()).isEqualTo(ComputeEnvironmentDiagnosis.STATUS_WARNING);
    assertThat(result.ready()).isTrue();
    assertThat(result.checks()).anyMatch(check -> check.message().contains("当前配置为"));
  }

  private void executable(Path path, String content) throws Exception {
    Files.writeString(path, content, StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
  }
}
