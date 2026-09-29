package io.yak.ops.business.lifecycle.dispatch;

import io.yak.ops.business.lifecycle.exception.LifecycleException;
import io.yak.ops.common.enums.lifecycle.LifecycleErrorCode;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 经统一数据源 SQL SPI 下传语句;凭据始终不出 datasource 模块。 */
@Slf4j
@Component
public class DataSourceTtlSqlGateway implements TtlSqlGateway {

  private final ObjectProvider<DataSourceExecutionProvider> provider;

  public DataSourceTtlSqlGateway(ObjectProvider<DataSourceExecutionProvider> provider) {
    this.provider = provider;
  }

  @Override
  public boolean available() {
    return provider.getIfAvailable() != null;
  }

  @Override
  public void execute(Long datasourceId, String sql, int timeoutSeconds) {
    run(datasourceId, sql, 1, timeoutSeconds);
  }

  @Override
  public List<List<Object>> query(Long datasourceId, String sql, int maxRows) {
    return run(datasourceId, sql, maxRows, 30);
  }

  private List<List<Object>> run(Long datasourceId, String sql, int maxRows, int timeoutSeconds) {
    DataSourceExecutionProvider executionProvider = provider.getIfAvailable();
    if (executionProvider == null) {
      throw new LifecycleException(LifecycleErrorCode.STORAGE_NOT_DISPATCHABLE,
          "平台未装配数据源 SQL 执行能力");
    }
    if (datasourceId == null) {
      throw new LifecycleException(LifecycleErrorCode.LAYER_CONFIG_MISSING, "分层未配置数据源");
    }
    try (DataSourceSqlExecutor executor = executionProvider.open(String.valueOf(datasourceId))) {
      DataSourceSqlResult result =
          executor.execute(new DataSourceSqlRequest(sql, maxRows, timeoutSeconds));
      return result.rows();
    } catch (Exception e) {
      log.warn("TTL SQL execution failed, datasourceId={}, sql={}", datasourceId, sql, e);
      throw new LifecycleException(LifecycleErrorCode.DISPATCH_FAILED, rootMessage(e), e);
    }
  }

  private static String rootMessage(Throwable e) {
    Throwable cur = e;
    while (cur.getCause() != null && cur.getCause() != cur) {
      cur = cur.getCause();
    }
    String msg = cur.getMessage();
    return msg == null ? cur.getClass().getSimpleName() : msg;
  }
}
