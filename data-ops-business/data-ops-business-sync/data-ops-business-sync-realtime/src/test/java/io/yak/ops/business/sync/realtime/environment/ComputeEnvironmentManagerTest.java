package io.yak.ops.business.sync.realtime.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.SshConfig;
import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ComputeEnvironmentManagerTest {

  private ComputeEnvironmentStore store;
  private ComputeEnvironmentManager manager;

  @BeforeEach
  void setUp() {
    store = mock(ComputeEnvironmentStore.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    manager =
        new ComputeEnvironmentManager(
            store, new ComputeEnvironmentConfigNormalizer(), transactionManager);
  }

  @Test
  void createsSshEnvironmentWithNormalizedConnectionSettings() {
    when(store.insert(any(), any(), any(), any(), any(), eq(true), eq(false))).thenReturn(11L);
    RuntimeConfig config =
        new RuntimeConfig(
            "http://10.0.0.20:8081",
            "/opt/flink",
            "/opt/flink-cdc",
            "/usr/lib/jvm/java-17",
            "1.20.5",
            "3.6.0",
            new SshConfig(
                " ssh ",
                "10.0.0.30",
                22,
                "flink",
                " C:\\keys\\flink_ed25519 ",
                null,
                true,
                8,
                "flink-jm.internal",
                8081));

    long id = manager.create("生产 SSH 环境", "ssh", config, true, false);

    assertThat(id).isEqualTo(11L);
    ArgumentCaptor<RuntimeConfig> captured = ArgumentCaptor.forClass(RuntimeConfig.class);
    verify(store)
        .insert(
            eq("生产 SSH 环境"),
            eq(ComputeEnvironment.ENGINE_FLINK_CDC),
            eq(ComputeEnvironment.DEPLOYMENT_REMOTE),
            eq(ComputeEnvironment.SUBMITTER_SSH),
            captured.capture(),
            eq(true),
            eq(false));
    RuntimeConfig saved = captured.getValue();
    assertThat(saved.ssh().executable()).isEqualTo("ssh");
    assertThat(saved.ssh().identityFile()).isEqualTo("C:\\keys\\flink_ed25519");
    assertThat(saved.ssh().remoteRestAddress()).isEqualTo("flink-jm.internal");
  }

  @Test
  void rejectsSshEnvironmentWithoutSubmissionHost() {
    RuntimeConfig config =
        new RuntimeConfig(
            "http://10.0.0.20:8081",
            "/opt/flink",
            "/opt/flink-cdc",
            null,
            "1.20.5",
            "3.6.0",
            new SshConfig("ssh", null, 22, "flink", null, null, true, 5, null, null));

    assertThatThrownBy(() -> manager.create("bad", "SSH", config, true, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SSH Host");
  }

  @Test
  void rejectsWindowsRuntimePathForRemoteLinuxSubmission() {
    RuntimeConfig config =
        new RuntimeConfig(
            "http://10.0.0.20:8081",
            "C:\\flink",
            "C:\\flink-cdc",
            null,
            "1.20.5",
            "3.6.0",
            new SshConfig("ssh", "10.0.0.30", 22, "flink", null, null, true, 5, null, null));

    assertThatThrownBy(() -> manager.create("bad-path", "SSH", config, true, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Linux 绝对路径");
  }
}
