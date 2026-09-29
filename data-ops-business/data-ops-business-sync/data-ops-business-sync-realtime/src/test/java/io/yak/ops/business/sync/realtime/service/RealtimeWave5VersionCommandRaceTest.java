package io.yak.ops.business.sync.realtime.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.PublishedDefinitionRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RealtimeWave5VersionCommandRaceTest {

  @Test
  void stopThatWinsDuringPreflightPreventsPublishedVersionReplacement() {
    long taskId = 7L;
    long executionId = 19L;
    long v3Id = 31L;
    long v4Id = 32L;
    CdcPipelineSpec v3 = spec("orders_v3");
    CdcPipelineSpec v4 = spec("orders_v4");
    ComputeEnvironmentSnapshot environment = environment();
    DefinitionRow task =
        new DefinitionRow(
            taskId,
            "orders-sync",
            null,
            v4,
            environment.id(),
            "PUBLISHED",
            "RUNNING",
            "RUNNING",
            4,
            4,
            v4Id,
            "c".repeat(64),
            null,
            LocalDateTime.now(),
            LocalDateTime.now());
    DeploymentRow running =
        execution(
            executionId,
            taskId,
            v3Id,
            v3,
            environment,
            "RUNNING",
            "RUNNING",
            "RUNNING");
    DeploymentRow stopAlreadyWon =
        execution(
            executionId,
            taskId,
            v3Id,
            v3,
            environment,
            "STOPPED",
            "STOPPING",
            "STOPPING");
    PublishedDefinitionRow published =
        new PublishedDefinitionRow(
            v4Id,
            taskId,
            4,
            4,
            v4,
            environment.id(),
            "a".repeat(64),
            "b".repeat(64));

    RealtimeJobStore store = mock(RealtimeJobStore.class);
    CdcPipelineSpecValidator specValidator = mock(CdcPipelineSpecValidator.class);
    RealtimeDataSourceResolver dataSourceResolver = mock(RealtimeDataSourceResolver.class);
    RealtimeConnectorCapabilityResolver capabilityResolver =
        mock(RealtimeConnectorCapabilityResolver.class);
    PipelineYamlCompiler compiler = mock(PipelineYamlCompiler.class);
    RealtimeEngineGateway gateway = mock(RealtimeEngineGateway.class);
    RealtimeRuntimeResolver runtimeResolver = mock(RealtimeRuntimeResolver.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

    ResolvedCdcPipeline resolved = mock(ResolvedCdcPipeline.class);
    CompiledPipeline compiled = new CompiledPipeline("pipeline: v4", "v4");
    when(store.deploymentByIdempotencyKey("apply-race")).thenReturn(Optional.empty());
    when(store.latestDeployment(taskId))
        .thenReturn(Optional.of(running), Optional.of(running), Optional.of(stopAlreadyWon));
    when(store.publishedDefinition(taskId)).thenReturn(Optional.of(published));
    when(store.definition(taskId)).thenReturn(Optional.of(task));
    when(store.lockDefinition(taskId)).thenReturn(task);
    when(runtimeResolver.environment(environment.id(), true)).thenReturn(environment);
    when(dataSourceResolver.resolve(v4)).thenReturn(resolved);
    when(gateway.capabilities(environment)).thenReturn(new ObjectMapper().createObjectNode());
    when(compiler.compile("orders-sync", v4, resolved)).thenReturn(compiled);
    when(gateway.validate(environment, compiled.yaml()))
        .thenReturn(new ValidationResult(true, "at-least-once"));

    RealtimeJobService service =
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

    assertThatThrownBy(() -> service.applyPublishedVersion(taskId, "apply-race"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("请先对账");

    verify(store, never())
        .reserveReplacementStop(anyLong(), anyLong(), any(), anyLong(), any());
    verify(store, never()).insertDeployment(any(), any(), any(), any(), any(), any());
    verify(gateway, never()).stop(any(), any());
  }

  private static DeploymentRow execution(
      long executionId,
      long taskId,
      long versionId,
      CdcPipelineSpec spec,
      ComputeEnvironmentSnapshot environment,
      String desired,
      String observed,
      String status) {
    return new DeploymentRow(
        executionId,
        taskId,
        versionId,
        3,
        spec,
        "test",
        "d".repeat(64),
        "exec-" + executionId,
        "job-v3",
        environment.runtimeRevision(),
        environment,
        "FLINK_CDC",
        desired,
        observed,
        status,
        false,
        null,
        LocalDateTime.now(),
        LocalDateTime.now());
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
