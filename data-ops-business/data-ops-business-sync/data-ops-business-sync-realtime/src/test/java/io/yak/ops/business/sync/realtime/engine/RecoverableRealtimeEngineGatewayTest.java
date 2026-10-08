package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.engine.RealtimeDeployRequest.CredentialBinding;
import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecoverableRealtimeEngineGatewayTest {

  @Test
  void bindsProjectNamespacedIdentityBeforeDelegatingSubmission() {
    FlinkCdcEngineGateway delegate = mock(FlinkCdcEngineGateway.class);
    RealtimeRuntimeIdentityStore identityStore = mock(RealtimeRuntimeIdentityStore.class);
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    RecoverableRealtimeEngineGateway gateway =
        new RecoverableRealtimeEngineGateway(delegate, identityStore, currentProject);
    ComputeEnvironmentSnapshot environment = environment();
    CredentialBinding source = new CredentialBinding("source", "source-secret");
    CredentialBinding sink = new CredentialBinding("sink", "sink-secret");
    RealtimeDeployRequest request =
        new RealtimeDeployRequest("pipeline:\n  name: demo\n", "deploy-key", source, sink);
    when(delegate.deploy(eq(environment), any()))
        .thenReturn(
            new RealtimeEngineGateway.DeployResult(
                "0123456789abcdef0123456789abcdef", "at-least-once"));

    gateway.deploy(environment, request);

    String runtimeName = RealtimeRuntimeIdentity.jobName("7:deploy-key");
    verify(identityStore).bind("deploy-key", runtimeName);
    ArgumentCaptor<RealtimeDeployRequest> captured =
        ArgumentCaptor.forClass(RealtimeDeployRequest.class);
    verify(delegate).deploy(eq(environment), captured.capture());
    assertThat(captured.getValue().pipelineYaml()).contains("name: " + runtimeName);
    assertThat(captured.getValue().idempotencyKey()).isEqualTo("deploy-key");
    assertThat(captured.getValue().source()).isSameAs(source);
    assertThat(captured.getValue().sink()).isSameAs(sink);
  }

  @Test
  void aDeploymentWithoutVerifiedProjectNeverReachesRuntimeOrIdentityStorage() {
    FlinkCdcEngineGateway delegate = mock(FlinkCdcEngineGateway.class);
    RealtimeRuntimeIdentityStore store = mock(RealtimeRuntimeIdentityStore.class);
    CurrentProject missing = Optional::empty;
    RecoverableRealtimeEngineGateway gateway =
        new RecoverableRealtimeEngineGateway(delegate, store, missing);

    RealtimeDeployRequest request = new RealtimeDeployRequest(
        "pipeline:\n  name: demo\n", "shared-client-key",
        new CredentialBinding("source", "credential-one"),
        new CredentialBinding("sink", "credential-two"));

    assertThatThrownBy(() -> gateway.deploy(environment(), request))
        .isInstanceOf(io.yak.ops.core.project.ProjectContextException.class);
    verifyNoInteractions(store, delegate);
  }

  @Test
  void identicalIdempotencyKeysAcrossProjectsYieldDifferentRuntimeIdentities() {
    FlinkCdcEngineGateway delegate = mock(FlinkCdcEngineGateway.class);
    RealtimeRuntimeIdentityStore projectSeven = mock(RealtimeRuntimeIdentityStore.class);
    RealtimeRuntimeIdentityStore projectEight = mock(RealtimeRuntimeIdentityStore.class);
    RealtimeDeployRequest request = new RealtimeDeployRequest(
        "pipeline:\n  name: demo\n", "shared-client-key",
        new CredentialBinding("source", "credential-one"),
        new CredentialBinding("sink", "credential-two"));
    when(delegate.deploy(eq(environment()), any())).thenReturn(
        new RealtimeEngineGateway.DeployResult("runtime-id", "at-least-once"));

    new RecoverableRealtimeEngineGateway(
        delegate, projectSeven, () -> Optional.of(new ProjectContext(7L, "A")))
        .deploy(environment(), request);
    new RecoverableRealtimeEngineGateway(
        delegate, projectEight, () -> Optional.of(new ProjectContext(8L, "B")))
        .deploy(environment(), request);

    String first = RealtimeRuntimeIdentity.jobName("7:shared-client-key");
    String second = RealtimeRuntimeIdentity.jobName("8:shared-client-key");
    assertThat(first).isNotEqualTo(second);
    verify(projectSeven).bind("shared-client-key", first);
    verify(projectEight).bind("shared-client-key", second);
  }

  private ComputeEnvironmentSnapshot environment() {
    return new ComputeEnvironmentSnapshot(
        3L,
        "test-env",
        ComputeEnvironment.ENGINE_FLINK_CDC,
        ComputeEnvironment.DEPLOYMENT_REMOTE,
        ComputeEnvironment.SUBMITTER_LOCAL,
        new RuntimeConfig(
            "http://127.0.0.1:8081", "/opt/flink", "/opt/flink-cdc", null, "1.20.5", "3.6.0"),
        1);
  }
}
