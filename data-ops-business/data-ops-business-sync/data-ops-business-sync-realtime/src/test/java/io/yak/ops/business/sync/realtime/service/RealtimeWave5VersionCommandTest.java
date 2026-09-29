package io.yak.ops.business.sync.realtime.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.DesiredState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.domain.SyncExecution.EngineExecutionRef;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler.CompiledPipeline;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDeployRequest.CredentialBinding;
import io.yak.ops.business.sync.realtime.engine.RealtimeDeployRequest.CredentialBindings;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.DeployResult;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.RuntimeStatus;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.ValidationResult;
import io.yak.ops.business.sync.realtime.engine.ResolvedCdcPipeline;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.PublishedDefinitionRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RealtimeWave5VersionCommandTest {

  private static final long TASK_ID = 7L;
  private static final long OLD_EXECUTION_ID = 19L;
  private static final long NEW_EXECUTION_ID = 20L;
  private static final long V3_ID = 31L;
  private static final long V4_ID = 32L;

  private RealtimeJobStore store;
  private CdcPipelineSpecValidator specValidator;
  private RealtimeDataSourceResolver dataSourceResolver;
  private RealtimeConnectorCapabilityResolver capabilityResolver;
  private PipelineYamlCompiler compiler;
  private RealtimeEngineGateway gateway;
  private RealtimeRuntimeResolver runtimeResolver;
  private RealtimeJobService service;
  private ComputeEnvironmentSnapshot environment;
  private CdcPipelineSpec v3Spec;
  private CdcPipelineSpec v4Spec;
  private DefinitionRow task;

  @BeforeEach
  void setUp() {
    store = mock(RealtimeJobStore.class);
    specValidator = mock(CdcPipelineSpecValidator.class);
    dataSourceResolver = mock(RealtimeDataSourceResolver.class);
    capabilityResolver = mock(RealtimeConnectorCapabilityResolver.class);
    compiler = mock(PipelineYamlCompiler.class);
    gateway = mock(RealtimeEngineGateway.class);
    runtimeResolver = mock(RealtimeRuntimeResolver.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

    environment = environment();
    v3Spec = spec("orders_v3");
    v4Spec = spec("orders_v4");
    task = task();

    service =
        new RealtimeJobService(
            store,
            specValidator,
            new SyncExecutionStateMachine(),
            dataSourceResolver,
            capabilityResolver,
            compiler,
            gateway,
            runtimeResolver,
            transactionManager);
  }

  @Test
  void restartExecutionPreflightsRunningVersionEvenWhenPublishedRefIsNewer() {
    DeploymentRow running = runningRow(V3_ID, v3Spec, "job-v3");
    PublishedDefinitionRow v3 = version(V3_ID, 3, 3, v3Spec);
    PublishedDefinitionRow v4 = version(V4_ID, 4, 4, v4Spec);
    ResolvedCdcPipeline resolved = mock(ResolvedCdcPipeline.class);
    CompiledPipeline compiled = new CompiledPipeline("pipeline: v3", "v3");

    when(store.deploymentByIdempotencyKey("restart-v3")).thenReturn(Optional.empty());
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(running));
    when(store.definitionVersion(TASK_ID, V3_ID)).thenReturn(Optional.of(v3));
    when(store.publishedDefinition(TASK_ID)).thenReturn(Optional.of(v4));
    when(store.definition(TASK_ID)).thenReturn(Optional.of(task));
    when(runtimeResolver.environment(environment.id(), true)).thenReturn(environment);
    when(dataSourceResolver.resolve(v3Spec)).thenReturn(resolved);
    when(gateway.capabilities(environment)).thenReturn(new ObjectMapper().createObjectNode());
    when(compiler.compile("orders-sync", v3Spec, resolved)).thenReturn(compiled);
    when(gateway.validate(environment, compiled.yaml()))
        .thenThrow(new IllegalStateException("target-preflight-failed"));

    assertThatThrownBy(() -> service.restartExecution(TASK_ID, "restart-v3"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("target-preflight-failed");

    verify(store).definitionVersion(TASK_ID, V3_ID);
    verify(store, never()).publishedDefinition(TASK_ID);
    verify(compiler).compile("orders-sync", v3Spec, resolved);
    verify(compiler, never()).compile(eq("orders-sync"), eq(v4Spec), any());
    verify(store, never())
        .reserveReplacementStop(anyLong(), anyLong(), any(), anyLong(), any());
    verify(gateway, never()).stop(any(), any());
  }

  @Test
  void applyPublishedVersionPreflightsPinnedPublishedTargetBeforeStop() {
    DeploymentRow running = runningRow(V3_ID, v3Spec, "job-v3");
    PublishedDefinitionRow v4 = version(V4_ID, 4, 4, v4Spec);
    ResolvedCdcPipeline resolved = mock(ResolvedCdcPipeline.class);
    CompiledPipeline compiled = new CompiledPipeline("pipeline: v4", "v4");

    when(store.deploymentByIdempotencyKey("apply-v4")).thenReturn(Optional.empty());
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(running));
    when(store.publishedDefinition(TASK_ID)).thenReturn(Optional.of(v4));
    when(store.definition(TASK_ID)).thenReturn(Optional.of(task));
    when(runtimeResolver.environment(environment.id(), true)).thenReturn(environment);
    when(dataSourceResolver.resolve(v4Spec)).thenReturn(resolved);
    when(gateway.capabilities(environment)).thenReturn(new ObjectMapper().createObjectNode());
    when(compiler.compile("orders-sync", v4Spec, resolved)).thenReturn(compiled);
    when(gateway.validate(environment, compiled.yaml()))
        .thenThrow(new IllegalStateException("published-target-invalid"));

    assertThatThrownBy(() -> service.applyPublishedVersion(TASK_ID, "apply-v4"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("published-target-invalid");

    verify(store).publishedDefinition(TASK_ID);
    verify(compiler).compile("orders-sync", v4Spec, resolved);
    verify(store, never())
        .reserveReplacementStop(anyLong(), anyLong(), any(), anyLong(), any());
    verify(gateway, never()).stop(any(), any());
  }

  @Test
  void applyPublishedVersionRejectsWhenExecutionAlreadyUsesPublishedVersion() {
    DeploymentRow running = runningRow(V4_ID, v4Spec, "job-v4");
    PublishedDefinitionRow v4 = version(V4_ID, 4, 4, v4Spec);

    when(store.deploymentByIdempotencyKey("apply-same")).thenReturn(Optional.empty());
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(running));
    when(store.publishedDefinition(TASK_ID)).thenReturn(Optional.of(v4));

    assertThatThrownBy(() -> service.applyPublishedVersion(TASK_ID, "apply-same"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("已经运行最新已发布");

    verify(compiler, never()).compile(any(), any(), any());
    verify(store, never())
        .reserveReplacementStop(anyLong(), anyLong(), any(), anyLong(), any());
  }

  @Test
  void versionCommandsRejectUnknownExecutionInsteadOfBypassingReconcile() {
    DeploymentRow unknown =
        executionRow(
            V3_ID,
            v3Spec,
            "job-v3",
            DesiredState.RUNNING,
            ObservedState.UNKNOWN,
            true,
            "UNKNOWN");

    when(store.deploymentByIdempotencyKey("restart-unknown")).thenReturn(Optional.empty());
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(unknown));

    assertThatThrownBy(() -> service.restartExecution(TASK_ID, "restart-unknown"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("请先对账");

    verify(store, never()).definitionVersion(anyLong(), anyLong());
    verify(store, never())
        .reserveReplacementStop(anyLong(), anyLong(), any(), anyLong(), any());
  }

  @Test
  void restartExecutionCreatesNewExecutionBoundToSameImmutableVersion() {
    DeploymentRow running = runningRow(V3_ID, v3Spec, "job-v3");
    DeploymentRow stopping =
        replacementRow(DesiredState.STOPPED, ObservedState.STOPPING, "STOPPING");
    DeploymentRow stopped =
        replacementRow(DesiredState.STOPPED, ObservedState.STOPPED, "STOPPED");
    DeploymentRow newStarting =
        executionRowWithId(
            NEW_EXECUTION_ID,
            V3_ID,
            v3Spec,
            null,
            DesiredState.RUNNING,
            ObservedState.STARTING,
            false,
            "SUBMITTING");
    DeploymentRow newRunning =
        executionRowWithId(
            NEW_EXECUTION_ID,
            V3_ID,
            v3Spec,
            "job-new-v3",
            DesiredState.RUNNING,
            ObservedState.RUNNING,
            false,
            "RUNNING");
    PublishedDefinitionRow v3 = version(V3_ID, 3, 3, v3Spec);
    ResolvedCdcPipeline resolved = mock(ResolvedCdcPipeline.class);
    CompiledPipeline compiled = new CompiledPipeline("pipeline: v3", "v3");

    when(store.deploymentByIdempotencyKey("restart-success")).thenReturn(Optional.empty());
    when(store.latestDeployment(TASK_ID))
        .thenReturn(
            Optional.of(running),
            Optional.of(running),
            Optional.of(running),
            Optional.of(stopping),
            Optional.of(stopping),
            Optional.of(stopping),
            Optional.of(stopped),
            Optional.of(newStarting),
            Optional.of(newRunning));
    when(store.definitionVersion(TASK_ID, V3_ID)).thenReturn(Optional.of(v3));
    when(store.definition(TASK_ID)).thenReturn(Optional.of(task));
    when(store.lockDefinition(TASK_ID)).thenReturn(task);
    when(store.latestExecution(TASK_ID))
        .thenReturn(
            Optional.of(
                execution(
                    OLD_EXECUTION_ID,
                    V3_ID,
                    DesiredState.STOPPED,
                    ObservedState.STOPPED,
                    "job-v3")),
            Optional.of(
                execution(
                    OLD_EXECUTION_ID,
                    V3_ID,
                    DesiredState.STOPPED,
                    ObservedState.STOPPED,
                    "job-v3")));
    when(runtimeResolver.environment(environment.id(), true)).thenReturn(environment);
    when(runtimeResolver.deployment(any(), any())).thenReturn(environment);
    when(dataSourceResolver.resolve(v3Spec)).thenReturn(resolved);
    when(dataSourceResolver.resolveCredentials(v3Spec))
        .thenReturn(
            new CredentialBindings(
                new CredentialBinding("source", "secret"),
                new CredentialBinding("sink", "secret")));
    when(gateway.capabilities(environment)).thenReturn(new ObjectMapper().createObjectNode());
    when(compiler.compile("orders-sync", v3Spec, resolved)).thenReturn(compiled);
    when(gateway.validate(environment, compiled.yaml()))
        .thenReturn(new ValidationResult(true, "at-least-once"));
    when(gateway.status(environment, "job-v3"))
        .thenReturn(
            new RuntimeStatus("job-v3", RuntimeStatus.State.RUNNING),
            new RuntimeStatus("job-v3", RuntimeStatus.State.TERMINATED));
    when(store.insertDeployment(
            any(), eq(v3Spec), any(), any(), eq(environment), eq("restart-success")))
        .thenReturn(NEW_EXECUTION_ID);
    when(gateway.deploy(eq(environment), any()))
        .thenReturn(new DeployResult("job-new-v3", "at-least-once"));

    assertThatCode(() -> service.restartExecution(TASK_ID, "restart-success"))
        .doesNotThrowAnyException();

    verify(store)
        .reserveReplacementStop(
            TASK_ID,
            OLD_EXECUTION_ID,
            "RESTART_EXECUTION",
            V3_ID,
            "restart-success");
    verify(store).bindDeploymentDefinitionVersion(NEW_EXECUTION_ID, V3_ID, 3);
    verify(store, never()).publishedDefinition(TASK_ID);
    verify(gateway).stop(environment, "job-v3");
    verify(store)
        .markDeploymentRunning(
            TASK_ID, NEW_EXECUTION_ID, "job-new-v3", environment.runtimeRevision());
  }

  private DefinitionRow task() {
    return new DefinitionRow(
        TASK_ID,
        "orders-sync",
        "test",
        v4Spec,
        environment.id(),
        "PUBLISHED",
        "RUNNING",
        "RUNNING",
        4,
        4,
        V4_ID,
        "c".repeat(64),
        null,
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  private PublishedDefinitionRow version(
      long id, int versionNo, int sourceDraftRevision, CdcPipelineSpec spec) {
    return new PublishedDefinitionRow(
        id,
        TASK_ID,
        versionNo,
        sourceDraftRevision,
        spec,
        environment.id(),
        "a".repeat(64),
        "b".repeat(64));
  }

  private DeploymentRow runningRow(long versionId, CdcPipelineSpec spec, String jobId) {
    return executionRow(
        versionId,
        spec,
        jobId,
        DesiredState.RUNNING,
        ObservedState.RUNNING,
        false,
        "RUNNING");
  }

  private DeploymentRow replacementRow(
      DesiredState desired, ObservedState observed, String status) {
    return new DeploymentRow(
        OLD_EXECUTION_ID,
        TASK_ID,
        V3_ID,
        3,
        v3Spec,
        "test",
        "d".repeat(64),
        "old-execution-key",
        "job-v3",
        environment.runtimeRevision(),
        environment,
        "FLINK_CDC",
        desired.name(),
        observed.name(),
        status,
        false,
        null,
        "RESTART_EXECUTION",
        V3_ID,
        "restart-success",
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  private DeploymentRow executionRow(
      long versionId,
      CdcPipelineSpec spec,
      String jobId,
      DesiredState desired,
      ObservedState observed,
      boolean uncertain,
      String status) {
    return executionRowWithId(
        OLD_EXECUTION_ID, versionId, spec, jobId, desired, observed, uncertain, status);
  }

  private DeploymentRow executionRowWithId(
      long executionId,
      long versionId,
      CdcPipelineSpec spec,
      String jobId,
      DesiredState desired,
      ObservedState observed,
      boolean uncertain,
      String status) {
    return new DeploymentRow(
        executionId,
        TASK_ID,
        versionId,
        3,
        spec,
        "test",
        "d".repeat(64),
        "execution-" + executionId,
        jobId,
        environment.runtimeRevision(),
        environment,
        "FLINK_CDC",
        desired.name(),
        observed.name(),
        status,
        uncertain,
        null,
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  private SyncExecution execution(
      long executionId,
      long versionId,
      DesiredState desired,
      ObservedState observed,
      String jobId) {
    return new SyncExecution(
        executionId,
        TASK_ID,
        versionId,
        desired,
        observed,
        new EngineExecutionRef("FLINK_CDC", jobId),
        observed == ObservedState.UNKNOWN,
        null);
  }

  private ComputeEnvironmentSnapshot environment() {
    return new ComputeEnvironmentSnapshot(
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
  }

  private CdcPipelineSpec spec(String table) {
    return new CdcPipelineSpec(
        1L,
        2L,
        List.of(
            new CdcPipelineSpec.TableRoute(
                table, "ods_" + table, CdcPipelineSpec.MatchMode.EXACT, List.of("id"))),
        "initial",
        CdcPipelineSpec.SchemaEvolution.EVOLVE,
        1,
        60_000,
        new CdcPipelineSpec.RestartPolicy("fixed-delay", 3, 10_000),
        new CdcPipelineSpec.SinkTuning(3, 1_000, 2_000, 16_777_216, 128, true));
  }
}
