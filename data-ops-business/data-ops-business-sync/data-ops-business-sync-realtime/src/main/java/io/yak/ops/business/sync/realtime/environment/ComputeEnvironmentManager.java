package io.yak.ops.business.sync.realtime.environment;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Owns Compute Environment persistence, default selection and lifecycle constraints. */
@Component
@DependsOn("realtimeSyncFlyway")
public class ComputeEnvironmentManager {

  private final ComputeEnvironmentStore store;
  private final ComputeEnvironmentConfigNormalizer normalizer;
  private final TransactionTemplate transactions;

  public ComputeEnvironmentManager(
      ComputeEnvironmentStore store,
      ComputeEnvironmentConfigNormalizer normalizer,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager) {
    this.store = store;
    this.normalizer = normalizer;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public List<ComputeEnvironment> list() {
    return store.list();
  }

  public ComputeEnvironment get(long id) {
    return require(id);
  }

  public long create(
      String name,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean makeDefault) {
    String normalizedName = normalizer.normalizeName(name);
    String normalizedSubmitter =
        normalizer.normalizeSubmitterType(submitterType, ComputeEnvironment.SUBMITTER_LOCAL);
    RuntimeConfig normalizedConfig = normalizer.normalizeConfig(config, normalizedSubmitter, true);
    if (makeDefault && !enabled) {
      throw new IllegalArgumentException("默认运行环境必须保持启用");
    }

    try {
      Long id =
          transactions.execute(
              status -> {
                if (makeDefault) {
                  store.clearDefault();
                }
                return store.insert(
                    normalizedName,
                    ComputeEnvironment.ENGINE_FLINK_CDC,
                    ComputeEnvironment.DEPLOYMENT_REMOTE,
                    normalizedSubmitter,
                    normalizedConfig,
                    enabled,
                    makeDefault);
              });
      return Objects.requireNonNull(id, "新增运行环境失败");
    } catch (DuplicateKeyException exception) {
      throw new IllegalArgumentException("运行环境名称已存在：" + normalizedName, exception);
    }
  }

  public void update(
      long id,
      String name,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean makeDefault) {
    ComputeEnvironment current = require(id);
    String normalizedName = normalizer.normalizeName(name);
    String normalizedSubmitter =
        normalizer.normalizeSubmitterType(submitterType, current.submitterType());
    RuntimeConfig requestedConfig =
        normalizer.preserveExistingSshConfig(current, normalizedSubmitter, config);
    RuntimeConfig normalizedConfig =
        normalizer.normalizeConfig(requestedConfig, normalizedSubmitter, true);
    boolean switchingDefault = makeDefault && !current.defaultEnvironment();

    if (current.defaultEnvironment() && !enabled) {
      throw new IllegalStateException("默认运行环境不能停用，请先切换默认环境");
    }
    if (switchingDefault && !enabled) {
      throw new IllegalArgumentException("默认运行环境必须保持启用");
    }

    try {
      transactions.executeWithoutResult(
          status -> {
            store.update(id, normalizedName, normalizedSubmitter, normalizedConfig, enabled);
            if (switchingDefault) {
              store.clearDefault();
              store.setDefault(id);
            }
          });
    } catch (DuplicateKeyException exception) {
      throw new IllegalArgumentException("运行环境名称已存在：" + normalizedName, exception);
    }
  }

  public void setEnabled(long id, boolean enabled) {
    ComputeEnvironment current = require(id);
    if (current.defaultEnvironment() && !enabled) {
      throw new IllegalStateException("默认运行环境不能停用，请先切换默认环境");
    }
    store.setEnabled(id, enabled);
  }

  public void setDefault(long id) {
    ComputeEnvironment target = require(id);
    if (target.defaultEnvironment()) {
      return;
    }
    if (!target.enabled()) {
      throw new IllegalStateException("只有已启用的运行环境才能设为默认环境");
    }
    transactions.executeWithoutResult(
        status -> {
          store.clearDefault();
          store.setDefault(id);
        });
  }

  public void delete(long id) {
    ComputeEnvironment current = require(id);
    if (current.defaultEnvironment()) {
      throw new IllegalStateException("默认运行环境不能删除，请先切换默认环境");
    }
    if (store.hasRuntimeEnvironmentReferences(id)) {
      throw new IllegalStateException(
          "运行环境仍被实时同步 Draft、Published Version 或活动 Execution 引用，请先解除引用");
    }
    store.delete(id);
  }

  ComputeEnvironment require(long id) {
    return store.find(id).orElseThrow(() -> new IllegalArgumentException("运行环境不存在：" + id));
  }
}
