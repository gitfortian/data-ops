package io.yak.ops.business.sync.realtime.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler.CompiledPipeline;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
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

class RealtimeReplacementRecoveryTest {

  private static final long TASK_ID = 7L;
  private static final long SOURCE_EXECUTION_ID = 19L;
  private static final long V3_ID = 31L;
  private static final long V4_ID = 32L;

  private RealtimeJobStore store;
  private RealtimeDataSourceResolver dataSourceResolver;
  private PipelineYamlCompiler compiler;
  private RealtimeEngineGateway gateway;
  private RealtimeRuntimeResolver runtimeResolver;
  private RealtimeJobService service;
  private ComputeEnvironmentSnapshot environment;
  private CdcPipelineSpec v4Spec;
  private DefinitionRow task;
  private PublishedDefinitionRow v4;
  private DeploymentRow pendingApply;

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
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

    environment = environment();
    v4Spec = spec("orders_v4");
    task = task();
    v4 = version(V4_ID, 4, v4Spec);
    pendingApply = pendingApply();

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

    ResolvedCdcPipeline resolved = mock(ResolvedCdcPipeline.class);
    CompiledPipeline compiled = new CompiledPipeline("pipeline: v4", "v4");
    when(store.definition(TASK_ID)).thenReturn(Optional.of(task));
    when(runtimeResolver.environment(environment.id(), true)).thenReturn(environment);
    when(dataSourceResolver.resolve(v4Spec)).thenReturn(resolved);
    when(gateway.capabilities(environment)).thenReturn(new ObjectMapper().createObjectNode());
    when(compiler.compile("orders-sync", v4Spec, resolved)).thenReturn(compiled);
    when(gateway.validate(environment, compiled.yaml()))
        .thenReturn(new ValidationResult(true, "at-least-once"));
    when(store.lockDefinition(TASK_ID)).thenReturn(task);
  }

  @Test
  void ordinaryStartCannotStealSuccessorSlotFromStoppedPendingReplacement() {
    when(store.deploymentByIdempotencyKey("plain-start")).thenReturn(Optional.empty());
    when(store.publishedDefinition(TASK_ID)).thenReturn(Optional.of(v4));
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(pendingApply));

    assertThatThrownBy(() -> service.start(TASK_ID, "plain-start"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("版本替换");

    verify(store, never()).insertDeployment(any(), any(), any(), any(), any(), any());
  }

  @Test
  void sameIdempotencyKeyResumesStoppedApplyUsingPersistedTargetVersion() {
    when(store.deploymentByIdempotencyKey("apply-resume")).thenReturn(Optional.empty());
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(pendingApply));
    when(store.latestExecution(TASK_ID)).thenReturn(Optional.of(pendingApply.execution()));
    when(store.definitionVersion(TASK_ID, V4_ID)).thenReturn(Optional.of(v4));
    when(store.insertDeployment(
            any(), eq(v4Spec), any(), any(), eq(environment), eq("apply-resume")))
        .thenThrow(new IllegalStateException("successor-insert-reached"));

    assertThatThrownBy(() -> service.applyPublishedVersion(TASK_ID, "apply-resume"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("successor-insert-reached");

    verify(store, never()).publishedDefinition(TASK_ID);
    verify(store).definitionVersion(TASK_ID, V4_ID);
    verify(store)
        .insertDeployment(any(), eq(v4Spec), any(), any(), eq(environment), eq("apply-resume"));
  }

  private DeploymentRow pendingApply() {
    return new DeploymentRow(
        SOURCE_EXECUTION_ID,
        TASK_ID,
        V3_ID,
        3,
        spec("orders_v3"),
        "v3",
        "d".repeat(64),
        "old-execution-key",
        "job-v3",
        environment.runtimeRevision(),
        environment,
        "FLINK_CDC",
        "STOPPED",
        "STOPPED",
        "STOPPED",
        false,
        null,
        "APPLY_PUBLISHED_VERSION",
        V4_ID,
        "apply-resume",
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  private DefinitionRow task() {
    return new DefinitionRow(
        TASK_ID,
        "orders-sync",
        null,
        v4Spec,
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

  private PublishedDefinitionRow version(long id, int versionNo, CdcPipelineSpec spec) {
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

  private static CdcPipelineSpec spec(String table) {
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
