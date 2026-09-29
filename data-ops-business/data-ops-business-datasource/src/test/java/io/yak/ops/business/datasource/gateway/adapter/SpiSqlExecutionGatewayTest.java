package io.yak.ops.business.datasource.gateway.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.gateway.SqlExecutionGateway.Command;
import io.yak.ops.business.datasource.gateway.SqlExecutionGateway.Result;
import io.yak.ops.business.datasource.gateway.SqlExecutionGateway.Session;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpiSqlExecutionGatewayTest {

  @Test
  void mapsBusinessCommandAndPluginResultWithoutLeakingSpi() {
    DataSourceExecutionProvider provider = mock(DataSourceExecutionProvider.class);
    DataSourceSqlExecutor executor = mock(DataSourceSqlExecutor.class);
    when(provider.open("42")).thenReturn(executor);
    when(executor.execute(any(DataSourceSqlRequest.class)))
        .thenReturn(
            DataSourceSqlResult.resultSet(
                List.of(new DataSourceSqlColumn("name", "name", "VARCHAR", Types.VARCHAR, true)),
                List.of(Arrays.asList("Yak", null)),
                false));

    SpiSqlExecutionGateway gateway = new SpiSqlExecutionGateway(provider);
    try (Session session = gateway.open("42")) {
      Result result = session.execute(new Command("select name from demo", List.of(), 20, 5));

      assertThat(result.resultSet()).isTrue();
      assertThat(result.columns()).hasSize(1);
      assertThat(result.columns().getFirst().name()).isEqualTo("name");
      assertThat(result.rows().getFirst()).containsExactly("Yak", null);
    }

    verify(provider).open("42");
    verify(executor).close();
  }

  @Test
  void transactionAndCancellationCapabilitiesDelegateOnlyInsideAdapter() {
    DataSourceExecutionProvider provider = mock(DataSourceExecutionProvider.class);
    DataSourceSqlExecutor executor = mock(DataSourceSqlExecutor.class);
    when(provider.open("9")).thenReturn(executor);
    when(executor.supportsTransactions()).thenReturn(true);

    SpiSqlExecutionGateway gateway = new SpiSqlExecutionGateway(provider);
    try (Session session = gateway.open("9")) {
      assertThat(session.supportsTransactions()).isTrue();
      session.beginTransaction();
      session.cancel();
      session.rollbackTransaction();
    }

    verify(executor).beginTransaction();
    verify(executor).cancel();
    verify(executor).rollbackTransaction();
    verify(executor).close();
  }
}
