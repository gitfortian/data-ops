package io.yak.ops.business.sync.realtime.definition;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeValidationResult;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler.CompiledPipeline;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.ResolvedCdcPipeline;
import io.yak.ops.business.sync.realtime.environment.RealtimeRuntimeResolver;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Prepares, validates and publishes the current mutable Draft as an immutable DefinitionVersion. */
@Component
public class RealtimeDefinitionPublisher {

  private final RealtimeJobStore store;
  private final CdcPipelineSpecValidator specValidator;
  private final RealtimeDataSourceResolver dataSourceResolver;
  private final RealtimeConnectorCapabilityResolver capabilityResolver;
  private final PipelineYamlCompiler compiler;
  private final RealtimeEngineGateway gateway;
  private final RealtimeRuntimeResolver runtimeResolver;
  private final TransactionTemplate transactions;

  public RealtimeDefinitionPublisher(
      RealtimeJobStore store,
      CdcPipelineSpecValidator specValidator,
      RealtimeDataSourceResolver dataSourceResolver,
      RealtimeConnectorCapabilityResolver capabilityResolver,
      PipelineYamlCompiler compiler,
      RealtimeEngineGateway gateway,
      RealtimeRuntimeResolver runtimeResolver,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager) {
    this.store = store;
    this.specValidator = specValidator;
    this.dataSourceResolver = dataSourceResolver;
    this.capabilityResolver = capabilityResolver;
    this.compiler = compiler;
    this.gateway = gateway;
    this.runtimeResolver = runtimeResolver;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public void publish(long id) {
    PreparedDefinition prepared = prepare(id);
    gateway.validate(prepared.runtimeEnvironment(), prepared.compiled().yaml());

    transactions.executeWithoutResult(
        status -> {
          DefinitionRow locked = store.lockDefinition(id);
          requirePreparedDefinitionCurrent(prepared, locked);
          store.publish(
              id,
              prepared.definition().draftRevision(),
              prepared.definition().sourceConfigDigest());
          store.event(
              id,
              null,
              "PUBLISHED",
              locked.releaseState(),
              "PUBLISHED",
              "Flink CDC 校验通过，当前草稿已发布；已有 SyncExecution 继续运行原 DefinitionVersion");
        });
  }

  public RealtimeValidationResult validate(long id) {
    PreparedDefinition prepared = prepare(id);
    RealtimeEngineGateway.ValidationResult result =
        gateway.validate(prepared.runtimeEnvironment(), prepared.compiled().yaml());
    return new RealtimeValidationResult(result.valid(), result.deliverySemantics());
  }

  private PreparedDefinition prepare(long id) {
    DefinitionRow definition =
        store.definition(id)
            .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
    CdcPipelineSpec spec = store.spec(definition);
    specValidator.validate(spec);
    ComputeEnvironmentSnapshot runtimeEnvironment = runtimeResolver.definition(definition, true);
    ResolvedCdcPipeline resolved = dataSourceResolver.resolve(spec);
    JsonNode manifest = gateway.capabilities(runtimeEnvironment);
    capabilityResolver.requireSupported(manifest, resolved, spec);

    return new PreparedDefinition(
        definition,
        compiler.compile(definition.name(), spec, resolved),
        runtimeEnvironment);
  }

  private void requirePreparedDefinitionCurrent(
      PreparedDefinition prepared, DefinitionRow current) {
    DefinitionRow snapshot = prepared.definition();
    long currentRuntimeEnvironmentId = store.runtimeEnvironmentId(current.id());
    if (snapshot.draftRevision() != current.draftRevision()
        || !Objects.equals(snapshot.sourceConfigDigest(), current.sourceConfigDigest())
        || prepared.runtimeEnvironment().id() != currentRuntimeEnvironmentId) {
      throw new IllegalStateException("任务定义在校验期间已变化，请刷新后重试");
    }
  }

  private record PreparedDefinition(
      DefinitionRow definition,
      CompiledPipeline compiled,
      ComputeEnvironmentSnapshot runtimeEnvironment) {}
}
