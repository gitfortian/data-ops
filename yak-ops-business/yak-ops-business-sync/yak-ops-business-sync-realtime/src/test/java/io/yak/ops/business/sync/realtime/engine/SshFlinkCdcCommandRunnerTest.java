package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.SshConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SshFlinkCdcCommandRunnerTest {

  @TempDir Path temp;

  @Test
  void streamsPipelineOverStdinAndBuildsHardenedSshCommand() throws Exception {
    Path captured = temp.resolve("captured.yaml");
    Path args = temp.resolve("args.txt");
    Path ssh = fakeSsh(captured, args, 0);
    ComputeEnvironmentSnapshot environment = environment(ssh, "10.0.0.20", 22, "127.0.0.1", 8081);
    SshFlinkCdcCommandRunner runner = new SshFlinkCdcCommandRunner();

    URI rest = URI.create("http://10.0.0.20:8081");
    runner.validateReady(environment, rest);
    Path log = temp.resolve("submit.log");
    SshFlinkCdcCommandRunner.ExecutionResult result =
        runner.submit(environment, "pipeline:\n  name: demo\n", log, rest, Duration.ofSeconds(5));

    assertThat(result.exitCode()).isZero();
    assertThat(result.uncertain()).isFalse();
    assertThat(Files.readString(captured, StandardCharsets.UTF_8))
        .isEqualTo("pipeline:\n  name: demo\n");
    assertThat(Files.readString(log, StandardCharsets.UTF_8)).contains("Job ID:");
    assertThat(Files.readString(args, StandardCharsets.UTF_8))
        .contains("BatchMode=yes")
        .contains("StrictHostKeyChecking=yes")
        .contains("flink@10.0.0.20")
        .contains("/opt/flink-cdc/bin/flink-cdc.sh")
        .contains("-Drest.address=127.0.0.1")
        .contains("-Drest.port=8081");
  }

  @Test
  void treatsOpenSshExit255AsUncertainSubmission() throws Exception {
    Path ssh = fakeSsh(temp.resolve("ignored.yaml"), temp.resolve("args-255.txt"), 255);
    ComputeEnvironmentSnapshot environment = environment(ssh, "10.0.0.20", 22, null, null);
    SshFlinkCdcCommandRunner runner = new SshFlinkCdcCommandRunner();
    URI rest = URI.create("http://10.0.0.20:8081");

    runner.validateReady(environment, rest);
    SshFlinkCdcCommandRunner.ExecutionResult result =
        runner.submit(
            environment,
            "pipeline:\n  name: demo\n",
            temp.resolve("255.log"),
            rest,
            Duration.ofSeconds(5));

    assertThat(result.exitCode()).isEqualTo(255);
    assertThat(result.uncertain()).isTrue();
  }

  @Test
  void reportsIncompleteSshConfigurationBeforeStartingProcess() {
    SshFlinkCdcCommandRunner runner = new SshFlinkCdcCommandRunner();
    ComputeEnvironmentSnapshot environment =
        environment(Path.of("ssh"), null, 22, null, null);

    assertThat(runner.configurationError(environment)).contains("SSH host");
  }

  private ComputeEnvironmentSnapshot environment(
      Path sshExecutable, String host, int port, String remoteRestAddress, Integer remoteRestPort) {
    return new ComputeEnvironmentSnapshot(
        9L,
        "ssh-env",
        ComputeEnvironment.ENGINE_FLINK_CDC,
        ComputeEnvironment.DEPLOYMENT_REMOTE,
        ComputeEnvironment.SUBMITTER_SSH,
        new RuntimeConfig(
            "http://10.0.0.20:8081",
            "/opt/flink",
            "/opt/flink-cdc",
            null,
            "1.20.5",
            "3.6.0",
            new SshConfig(
                sshExecutable.toString(),
                host,
                port,
                "flink",
                null,
                null,
                true,
                1,
                remoteRestAddress,
                remoteRestPort)),
        1);
  }

  private Path fakeSsh(Path captured, Path args, int submitExitCode) throws Exception {
    Path script = temp.resolve("fake-ssh-" + submitExitCode + ".sh");
    String content =
        "#!/bin/sh\n"
            + "printf '%s\\n' \"$*\" > "
            + shell(args)
            + "\n"
            + "case \"$*\" in\n"
            + "  *YAK_REALTIME_SSH_READY*) exit 0 ;;\n"
            + "esac\n"
            + "cat > "
            + shell(captured)
            + "\n"
            + "echo 'Pipeline has been submitted to cluster.'\n"
            + "echo 'Job ID: 0123456789abcdef0123456789abcdef'\n"
            + "exit "
            + submitExitCode
            + "\n";
    Files.writeString(script, content, StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
    return script;
  }

  private String shell(Path path) {
    return "'" + path.toString().replace("'", "'\"'\"'") + "'";
  }
}
