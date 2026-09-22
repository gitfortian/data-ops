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
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec.MatchMode;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec.RestartPolicy;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec.SchemaEvolution;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec.SinkTuning;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec.TableRoute;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.DesiredState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler.CompiledPipeline;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineException;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
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

/** Stage 2 regression baseline for execution command safety semantics. */
class RealtimeExecutionCommandSafetyBaselineTest {

  private static final long TASK_ID = 7L;
  private static final long OTHER_TASK_ID = 8L;
  private static final long EXECUTION_ID = 19L;
  private static final long V3_ID = 31L;
  private static final long V4_ID = 32L;
  private static final String FLINK_JOB_ID = "0123456789abcdef0123456789abcdef";

  private RealtimeJobStore store;
  private RealtimeDataSourceResolver dataSourceResolver;
  private PipelineYamlCompiler compiler;
  private RealtimeEngineGateway gateway;
  private RealtimeRuntimeResolver runtimeResolver;
  private RealtimeJobService service;
  private CdcPipelineSpec spec;
  private ComputeEnvironmentSnapshot environment;
  private DefinitionRow task;
  private PublishedDefinitionRow published;

  @BeforeEach
  void setUp() {
    store = mock(RealtimeJobStore.class);
    CdcPipelineSpecValidator specValidator = mock(CdcPipelineSpecValidator.class);
    dataSourceResolver = mock(RealtimeDataSourceResolver.class);
    RealtimeConnectorCapabilityResolver capabilityResolver =
        mock(RealtimeConnectorCapabilityResolver.class);
    compiler = mock(PipelineYamlCompiler.class);
    gateway = mock(RealtimeEngineGateway.class);
    runtimeResolver = mock(RealtimeRuntimeResolver.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());

    environment = environment();
    spec = spec();
    task = task();
    published = version(V4_ID, 4);

    ObjectNode capabilities =
        new ObjectMapper().createObjectNode().put("runtimeVersion", "flink-cdc-cli-3.6.0");
    ResolvedCdcPipeline resolved = mock(ResolvedCdcPipeline.class);

    when(store.definition(TASK_ID)).thenReturn(Optional.of(task));
    when(store.publishedDefinition(TASK_ID)).thenReturn(Optional.of(published));
    when(store.deploymentByIdempotencyKey(any())).thenReturn(Optional.empty());
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.empty());
    when(store.lockDefinition(TASK_ID)).thenReturn(task);
    when(runtimeResolver.environment(anyLong(), eq(true))).thenReturn(environment);
    when(runtimeResolver.deployment(any(), any())).thenReturn(environment);
    when(dataSourceResolver.resolve(spec)).thenReturn(resolved);
    when(gateway.capabilities(environment)).thenReturn(capabilities);
    when(gateway.validate(eq(environment), any()))
        .thenReturn(new ValidationResult(true, "exactly-once"));
    when(compiler.compile(any(), eq(spec), eq(resolved)))
        .thenReturn(new CompiledPipeline("pipeline: baseline", "baseline"));

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
  void sameIdempotencyKeyReturnsExistingExecutionWithoutResubmitting() {
    DeploymentRow existing =
        execution(
            TASK_ID,
            V4_ID,
            "same-key",
            FLINK_JOB_ID,
            DesiredState.RUNNING,
            ObservedState.RUNNING,
            false,
            null,
            null,
            null);
    when(store.deploymentByIdempotencyKey("same-key")).thenReturn(Optional.of(existing));

    assertThatCode(() -> service.start(TASK_ID, "same-key")).doesNotThrowAnyException();

    verify(store).deploymentView(existing);
    verify(store, never()).publishedDefinition(TASK_ID);
    verify(store, never()).insertDeployment(any(), any(), any(), any(), any(), any());
    verify(gateway, never()).validate(any(), any());
    verify(gateway, never()).deploy(any(), any());
  }

  @Test
  void idempotencyKeyOwnedByAnotherTaskIsRejectedBeforeAnySubmit() {
    DeploymentRow foreign =
        execution(
            OTHER_TASK_ID,
            V4_ID,
            "shared-key",
            FLINK_JOB_ID,
            DesiredState.RUNNING,
            ObservedState.RUNNING,
            false,
            null,
            null,
            null);
    when(store.deploymentByIdempotencyKey("shared-key")).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> service.start(TASK_ID, "shared-key"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("幂等键已被其他实时任务使用");

    verify(store, never()).publishedDefinition(TASK_ID);
    verify(store, never()).insertDeployment(any(), any(), any(), any(), any(), any());
    verify(gateway, never()).deploy(any(), any());
  }

  @Test
  void unknownExecutionBlocksAnotherStartAtApplicationBoundary() {
    DeploymentRow unknown =
        execution(
            TASK_ID,
            V3_ID,
            "old-key",
            null,
            DesiredState.RUNNING,
            ObservedState.UNKNOWN,
            true,
            null,
            null,
            null);
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(unknown));

    assertThatThrownBy(() -> service.start(TASK_ID, "start-after-unknown"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("请勿重复启动");

    verify(store, never()).insertDeployment(any(), any(), any(), any(), any(), any());
    verify(gateway, never()).deploy(any(), any());
  }

  @Test
  void conflictExecutionBlocksAnotherStartAtApplicationBoundary() {
    DeploymentRow conflict =
        execution(
            TASK_ID,
            V3_ID,
            "old-key",
            null,
            DesiredState.RUNNING,
            ObservedState.CONFLICT,
            false,
            null,
            null,
            null);
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(conflict));

    assertThatThrownBy(() -> service.start(TASK_ID, "start-after-conflict"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("请勿重复启动");

    verify(store, never()).insertDeployment(any(), any(), any(), any(), any(), any());
    verify(gateway, never()).deploy(any(), any());
  }

  @Test
  void stopWithUnknownRuntimePreservesUnknownAndNeverPretendsStopped() {
    DeploymentRow running =
        execution(
            TASK_ID,
            V3_ID,
            "running-key",
            FLINK_JOB_ID,
            DesiredState.RUNNING,
            ObservedState.RUNNING,
            false,
            null,
            null,
            null);
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(running));
    when(gateway.status(environment, FLINK_JOB_ID))
        .thenReturn(new RuntimeStatus(FLINK_JOB_ID, RuntimeStatus.State.UNKNOWN));

    assertThatThrownBy(() -> service.stop(TASK_ID))
        .isInstanceOf(RealtimeEngineException.class)
        .hasMessageContaining("Flink 状态未知");

    verify(store).markStopping(TASK_ID, EXECUTION_ID);
    verify(store)
        .reconcile(
            TASK_ID,
            EXECUTION_ID,
            "UNKNOWN",
            "UNKNOWN",
            FLINK_JOB_ID,
            "Flink 状态未知，无法确认停止结果");
    verify(store, never())
        .reconcile(TASK_ID, EXECUTION_ID, "STOPPED", "STOPPED", FLINK_JOB_ID, null);
  }

  @Test
  void pendingReplacementRequiresItsOriginalIdempotencyKey() {
    DeploymentRow pendingApply = pendingApply("apply-original");
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(pendingApply));

    assertThatThrownBy(() -> service.applyPublishedVersion(TASK_ID, "apply-other"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("使用原 Idempotency-Key");

    verify(store, never()).definitionVersion(anyLong(), anyLong());
    verify(store, never()).insertDeployment(any(), any(), any(), any(), any(), any());
    verify(gateway, never()).deploy(any(), any());
  }

  @Test
  void pendingApplyCannotBeResumedAsRestartEvenWithTheSameKey() {
    DeploymentRow pendingApply = pendingApply("apply-original");
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(pendingApply));

    assertThatThrownBy(() -> service.restartExecution(TASK_ID, "apply-original"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("另一种版本替换命令");

    verify(store, never()).definitionVersion(anyLong(), anyLong());
    verify(store, never()).insertDeployment(any(), any(), any(), any(), any(), any());
    verify(gateway, never()).deploy(any(), any());
  }

  @Test
  void restartPinsTheCurrentExecutionVersionInsteadOfReadingLatestPublishedVersion() {
    DeploymentRow runningV3 =
        execution(
            TASK_ID,
            V3_ID,
            "running-v3",
            FLINK_JOB_ID,
            DesiredState.RUNNING,
            ObservedState.RUNNING,
            false,
            null,
            null,
            null);
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(runningV3));
    when(store.definitionVersion(TASK_ID, V3_ID))
        .thenThrow(new IllegalStateException("same-version-target-reached"));

    assertThatThrownBy(() -> service.restartExecution(TASK_ID, "restart-pin"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("same-version-target-reached");

    verify(store).definitionVersion(TASK_ID, V3_ID);
    verify(store, never()).publishedDefinition(TASK_ID);
    verify(store, never()).insertDeployment(any(), any(), any(), any(), any(), any());
  }

  private DeploymentRow pendingApply(String key) {
    return execution(
        TASK_ID,
        V3_ID,
        "running-v3",
        FLINK_JOB_ID,
        DesiredState.STOPPED,
        ObservedState.STOPPED,
        false,
        "APPLY_PUBLISHED_VERSION",
        V4_ID,
        key);
  }

  private DeploymentRow execution(
      long taskId,
      long definitionVersionId,
      String idempotencyKey,
      String engineJobId,
      DesiredState desired,
      ObservedState observed,
      boolean uncertain,
      String replacementCommand,
      Long replacementTarget,
      String replacementKey) {
    LocalDateTime now = LocalDateTime.now();
    return new DeploymentRow(
        EXECUTION_ID,
        taskId,
        definitionVersionId,
        3,
        spec,
        "baseline",
        "d".repeat(64),
        idempotencyKey,
        engineJobId,
        environment.runtimeRevision(),
        environment,
        "FLINK_CDC",
        desired.name(),
        observed.name(),
        observed.name(),
        uncertain,
        null,
        replacementCommand,
        replacementTarget,
        replacementKey,
        now,
        now);
  }

  private DefinitionRow task() {
    return new DefinitionRow(
        TASK_ID,
        "orders-sync",
        null,
        spec,
        environment.id(),
        "PUBLISHED",
        "STOPPED",
        "STOPPED",
        4,
        4,
        V4_ID,
        "c".repeat(64),
        null,
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  private PublishedDefinitionRow version(long id, int versionNo) {
    return new PublishedDefinitionRow(
        id,
        TASK_ID,
        versionNo,
        versionNo,
        spec,
        environment.id(),
        "a".repeat(64),
        "b".repeat(64));
  }

  private static ComputeEnvironmentSnapshot environment() {
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

  private static CdcPipelineSpec spec() {
    return new CdcPipelineSpec(
        1L,
        2L,
        List.of(new TableRoute("orders", "ods_orders", MatchMode.EXACT, List.of("id"))),
        "initial",
        SchemaEvolution.EVOLVE,
        1,
        60_000,
        new RestartPolicy("fixed-delay", 3, 10_000),
        new SinkTuning(3, 1_000, 2_000, 16_777_216, 128, true));
  }
}
