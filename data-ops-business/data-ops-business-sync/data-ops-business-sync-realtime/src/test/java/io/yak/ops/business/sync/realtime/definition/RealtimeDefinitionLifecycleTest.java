package io.yak.ops.business.sync.realtime.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import io.yak.ops.business.sync.realtime.environment.RealtimeRuntimeResolver;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RealtimeDefinitionLifecycleTest {

  private static final long TASK_ID = 7L;
  private static final long ENVIRONMENT_ID = 3L;

  @Test
  void sourceConfigDigestIncludesRuntimeEnvironmentBinding() {
    RealtimeSourceConfigDigestCalculator calculator =
        new RealtimeSourceConfigDigestCalculator(new ObjectMapper());
    CdcPipelineSpec spec = spec();

    String first = calculator.calculate(spec, ENVIRONMENT_ID);
    String same = calculator.calculate(spec, ENVIRONMENT_ID);
    String anotherEnvironment = calculator.calculate(spec, ENVIRONMENT_ID + 1);

    assertThat(first).hasSize(64).isEqualTo(same);
    assertThat(anotherEnvironment).isNotEqualTo(first);
  }

  @Test
  void saveDraftPersistsSourceConfigDigestThroughDefinitionManager() {
    RealtimeJobStore store = mock(RealtimeJobStore.class);
    CdcPipelineSpecValidator specValidator = mock(CdcPipelineSpecValidator.class);
    RealtimeRuntimeResolver runtimeResolver = mock(RealtimeRuntimeResolver.class);
    SyncExecutionStateMachine stateMachine = mock(SyncExecutionStateMachine.class);
    RealtimeSourceConfigDigestCalculator digest =
        mock(RealtimeSourceConfigDigestCalculator.class);
    PlatformTransactionManager transactionManager = transactionManager();
    ComputeEnvironmentSnapshot environment = environment();
    CdcPipelineSpec spec = spec();
    DefinitionRow locked = definition(1, "old-digest");

    when(runtimeResolver.environment(ENVIRONMENT_ID, true)).thenReturn(environment);
    when(digest.calculate(spec, ENVIRONMENT_ID)).thenReturn("new-source-digest");
    when(store.lockDefinition(TASK_ID)).thenReturn(locked);

    RealtimeDefinitionManager manager =
        new RealtimeDefinitionManager(
            store,
            specValidator,
            runtimeResolver,
            stateMachine,
            digest,
            transactionManager);

    long saved = manager.save(TASK_ID, "orders-sync", null, spec, ENVIRONMENT_ID);

    assertThat(saved).isEqualTo(TASK_ID);
    verify(specValidator).validate(spec);
    verify(runtimeResolver).environment(ENVIRONMENT_ID, true);
    verify(store)
        .updateDefinition(
            TASK_ID,
            "orders-sync",
            null,
            spec,
            "new-source-digest",
            ENVIRONMENT_ID);
    verify(store)
        .event(
            TASK_ID,
            null,
            "DRAFT_SAVED",
            "DRAFT",
            "DRAFT",
            "已保存草稿并生成新定义版本；当前 SyncExecution 不受影响");
  }

  @Test
  void publishRejectsDraftThatChangesAfterRuntimeValidation() {
    RealtimeJobStore store = mock(RealtimeJobStore.class);
    CdcPipelineSpecValidator specValidator = mock(CdcPipelineSpecValidator.class);
    RealtimeDataSourceResolver dataSourceResolver = mock(RealtimeDataSourceResolver.class);
    RealtimeConnectorCapabilityResolver capabilityResolver =
        mock(RealtimeConnectorCapabilityResolver.class);
    PipelineYamlCompiler compiler = mock(PipelineYamlCompiler.class);
    RealtimeEngineGateway gateway = mock(RealtimeEngineGateway.class);
    RealtimeRuntimeResolver runtimeResolver = mock(RealtimeRuntimeResolver.class);
    PlatformTransactionManager transactionManager = transactionManager();

    CdcPipelineSpec spec = spec();
    ComputeEnvironmentSnapshot environment = environment();
    DefinitionRow prepared = definition(1, "digest-v1");
    DefinitionRow changed = definition(2, "digest-v2");
    ResolvedCdcPipeline resolved = mock(ResolvedCdcPipeline.class);
    CompiledPipeline compiled = new CompiledPipeline("pipeline: frozen", "frozen");

    when(store.definition(TASK_ID)).thenReturn(java.util.Optional.of(prepared));
    when(store.spec(prepared)).thenReturn(spec);
    when(runtimeResolver.definition(prepared, true)).thenReturn(environment);
    when(dataSourceResolver.resolve(spec)).thenReturn(resolved);
    when(gateway.capabilities(environment)).thenReturn(new ObjectMapper().createObjectNode());
    when(compiler.compile("orders-sync", spec, resolved)).thenReturn(compiled);
    when(gateway.validate(environment, compiled.yaml()))
        .thenReturn(new ValidationResult(true, "at-least-once"));
    when(store.lockDefinition(TASK_ID)).thenReturn(changed);
    when(store.runtimeEnvironmentId(TASK_ID)).thenReturn(ENVIRONMENT_ID);

    RealtimeDefinitionPublisher publisher =
        new RealtimeDefinitionPublisher(
            store,
            specValidator,
            dataSourceResolver,
            capabilityResolver,
            compiler,
            gateway,
            runtimeResolver,
            transactionManager);

    assertThatThrownBy(() -> publisher.publish(TASK_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("定义在校验期间已变化");

    verify(gateway).validate(environment, compiled.yaml());
    verify(store, never()).publish(anyLong(), anyInt(), any());
  }

  private PlatformTransactionManager transactionManager() {
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    return transactionManager;
  }

  private DefinitionRow definition(int draftRevision, String digest) {
    return new DefinitionRow(
        TASK_ID,
        "orders-sync",
        null,
        spec(),
        ENVIRONMENT_ID,
        "DRAFT",
        "STOPPED",
        "STOPPED",
        draftRevision,
        null,
        digest,
        null,
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  private ComputeEnvironmentSnapshot environment() {
    return new ComputeEnvironmentSnapshot(
        ENVIRONMENT_ID,
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

  private CdcPipelineSpec spec() {
    return new CdcPipelineSpec(
        1L,
        2L,
        List.of(
            new CdcPipelineSpec.TableRoute(
                "orders", "orders", CdcPipelineSpec.MatchMode.EXACT, List.of("id"))),
        "initial",
        CdcPipelineSpec.SchemaEvolution.EVOLVE,
        1,
        60_000,
        new CdcPipelineSpec.RestartPolicy("fixed-delay", 3, 10_000),
        new CdcPipelineSpec.SinkTuning(3, 1_000, 2_000, 16_777_216, 128, true));
  }
}
