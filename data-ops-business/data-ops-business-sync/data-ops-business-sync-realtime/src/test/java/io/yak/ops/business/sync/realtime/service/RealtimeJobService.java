package io.yak.ops.business.sync.realtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionCoordinator;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionPreparation;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionReplacementManager;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionReservationManager;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionStarter;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionStateManager;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Test-scope source-compatible adapter for the long-lived execution regression suite.
 *
 * <p>This class is not production code and is not a Spring bean. Every command delegates to the
 * real decomposed Execution Core so historical behavioral assertions continue to exercise the
 * current production path without reintroducing a production compatibility facade.
 */
final class RealtimeJobService {

  private final RealtimeJobStore store;
  private final RealtimeExecutionCoordinator executions;
  private final RealtimeExecutionPreparation preparation;

  RealtimeJobService(
      RealtimeJobStore store,
      CdcPipelineSpecValidator specValidator,
      SyncExecutionStateMachine stateMachine,
      RealtimeDataSourceResolver dataSourceResolver,
      RealtimeConnectorCapabilityResolver capabilityResolver,
      PipelineYamlCompiler compiler,
      RealtimeEngineGateway gateway,
      RealtimeRuntimeResolver runtimeResolver,
      PlatformTransactionManager transactionManager) {
    this.store = store;
    this.preparation =
        new RealtimeExecutionPreparation(
            store,
            specValidator,
            dataSourceResolver,
            capabilityResolver,
            compiler,
            gateway,
            runtimeResolver);
    RealtimeExecutionReservationManager reservations =
        new RealtimeExecutionReservationManager(
            store, stateMachine, preparation, transactionManager);
    RealtimeExecutionStateManager states =
        new RealtimeExecutionStateManager(
            store, stateMachine, gateway, preparation, transactionManager);
    RealtimeExecutionStarter starter =
        new RealtimeExecutionStarter(preparation, reservations, states);
    RealtimeExecutionReplacementManager replacements =
        new RealtimeExecutionReplacementManager(
            preparation, reservations, states, starter);
    this.executions = new RealtimeExecutionCoordinator(starter, states, replacements);
  }

  RealtimeJobView get(long id) {
    return store.view(id);
  }

  RealtimeJobView.Deployment start(long id, String key) {
    return executions.start(id, key);
  }

  void stop(long id) {
    executions.stop(id);
  }

  RealtimeJobView.Deployment restartExecution(long id, String key) {
    return executions.restartExecution(id, key);
  }

  RealtimeJobView.Deployment applyPublishedVersion(long id, String key) {
    return executions.applyPublishedVersion(id, key);
  }

  List<RealtimeJobEventView> events(long id) {
    store.definition(id)
        .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
    return store.events(id);
  }

  JsonNode capabilities(long runtimeEnvironmentId) {
    return preparation.capabilities(runtimeEnvironmentId);
  }
}
