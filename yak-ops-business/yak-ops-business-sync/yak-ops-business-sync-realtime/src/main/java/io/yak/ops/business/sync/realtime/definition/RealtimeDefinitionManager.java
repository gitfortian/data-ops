package io.yak.ops.business.sync.realtime.definition;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.environment.RealtimeRuntimeResolver;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Owns mutable Draft persistence and task-definition metadata lifecycle. */
@Component
public class RealtimeDefinitionManager {

  private final RealtimeJobStore store;
  private final CdcPipelineSpecValidator specValidator;
  private final RealtimeRuntimeResolver runtimeResolver;
  private final SyncExecutionStateMachine executionStateMachine;
  private final RealtimeSourceConfigDigestCalculator digestCalculator;
  private final TransactionTemplate transactions;

  public RealtimeDefinitionManager(
      RealtimeJobStore store,
      CdcPipelineSpecValidator specValidator,
      RealtimeRuntimeResolver runtimeResolver,
      SyncExecutionStateMachine executionStateMachine,
      RealtimeSourceConfigDigestCalculator digestCalculator,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager) {
    this.store = store;
    this.specValidator = specValidator;
    this.runtimeResolver = runtimeResolver;
    this.executionStateMachine = executionStateMachine;
    this.digestCalculator = digestCalculator;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  /** Creates the task shell first; the editor supplies the pipeline spec in the second stage. */
  public long create(String name, String description, long runtimeEnvironmentId) {
    requireName(name);
    runtimeResolver.environment(runtimeEnvironmentId, true);
    Long saved =
        transactions.execute(
            status -> {
              long created =
                  store.insertDefinition(
                      name.trim(), description, null, null, runtimeEnvironmentId);
              store.event(created, null, "DRAFT_CREATED", null, "DRAFT", "已创建实时同步基础任务");
              return created;
            });
    return saved == null ? 0 : saved;
  }

  public long save(
      Long id,
      String name,
      String description,
      CdcPipelineSpec spec,
      long runtimeEnvironmentId) {
    requireName(name);
    specValidator.validate(spec);
    runtimeResolver.environment(runtimeEnvironmentId, true);
    String sourceConfigDigest = digestCalculator.calculate(spec, runtimeEnvironmentId);

    Long saved =
        transactions.execute(
            status -> {
              if (id == null) {
                long created =
                    store.insertDefinition(
                        name.trim(), description, spec, sourceConfigDigest, runtimeEnvironmentId);
                store.event(created, null, "DRAFT_CREATED", null, "DRAFT", "已创建实时同步草稿");
                return created;
              }

              DefinitionRow locked = store.lockDefinition(id);
              store.updateDefinition(
                  id,
                  name.trim(),
                  description,
                  spec,
                  sourceConfigDigest,
                  runtimeEnvironmentId);
              store.event(
                  id,
                  null,
                  "DRAFT_SAVED",
                  locked.releaseState(),
                  "DRAFT",
                  "已保存草稿并生成新定义版本；当前 SyncExecution 不受影响");
              return id;
            });
    return saved == null ? 0 : saved;
  }

  public void delete(long id) {
    transactions.executeWithoutResult(
        status -> {
          store.lockDefinition(id);
          executionStateMachine.requireDefinitionMutable(store.latestExecution(id).orElse(null));
          store.delete(id);
        });
  }

  private void requireName(String name) {
    if (!StringUtils.hasText(name) || name.trim().length() > 200) {
      throw new IllegalArgumentException("任务名称不能为空且不能超过 200 个字符");
    }
  }
}
