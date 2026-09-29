package io.yak.ops.plugin.database.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogReadRequest;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import io.yak.ops.spi.datasource.query.DataSourceQueryResult;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GenericJdbcCatalogTypedRequestTest {

  @Test
  void resolveSqlUsesTypedVariablesWithoutLegacyMapKeys() {
    GenericJdbcCatalog catalog = new GenericJdbcCatalog(connection(), 5);
    DataSourceCatalogReadRequest request =
        DataSourceCatalogReadRequest.sql(
            "select '${day}' as day_value, ${var:tenant} as tenant_value",
            Map.of("day", "2026-08-23", "tenant", "42"));

    String resolved = catalog.resolveSql(request.query(), request);

    assertThat(resolved)
        .isEqualTo("select '2026-08-23' as day_value, 42 as tenant_value");
  }

  @Test
  void typedRequestRejectsMissingModePayload() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new DataSourceCatalogReadRequest(
                DataSourceCatalogReadRequest.Mode.SQL, null, null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("query");
  }

  @Test
  void previewDoesNotCountAndAppliesSqlAndJdbcLimits() throws Exception {
    Connection opened = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    ResultSetMetaData metadata = mock(ResultSetMetaData.class);
    AtomicInteger countCalls = new AtomicInteger();

    when(opened.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.getMetaData()).thenReturn(metadata);
    when(metadata.getColumnCount()).thenReturn(1);
    when(metadata.getColumnLabel(1)).thenReturn("id");
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getObject(1)).thenReturn(7L);

    GenericJdbcCatalog catalog =
        new GenericJdbcCatalog(connection(), 5, 13) {
          @Override
          protected Connection openConnection() {
            return opened;
          }

          @Override
          public long count(DataSourceCatalogReadRequest request) {
            countCalls.incrementAndGet();
            return 99L;
          }
        };

    DataSourceQueryResult result =
        catalog.preview(DataSourceCatalogReadRequest.table("orders", Map.of()), 20);

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(opened).prepareStatement(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue()).isEqualTo("SELECT * FROM `demo`.`orders` LIMIT 20");
    assertThat(result.getData()).hasSize(1);
    assertThat(result.getTotal()).isEqualTo(1L);
    assertThat(countCalls.get()).isZero();
    verify(statement).setQueryTimeout(13);
    verify(statement).setMaxRows(20);
  }

  @Test
  void customSqlPreviewAddsOuterLimitWithoutRewritingUserSql() throws Exception {
    Connection opened = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    ResultSetMetaData metadata = mock(ResultSetMetaData.class);

    when(opened.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.getMetaData()).thenReturn(metadata);
    when(metadata.getColumnCount()).thenReturn(0);
    when(resultSet.next()).thenReturn(false);

    GenericJdbcCatalog catalog =
        new GenericJdbcCatalog(connection(), 5, 13) {
          @Override
          protected Connection openConnection() {
            return opened;
          }
        };

    catalog.preview(
        DataSourceCatalogReadRequest.sql(
            "select * from orders order by created_at desc limit 1000;", Map.of()),
        20);

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(opened).prepareStatement(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue())
        .isEqualTo(
            "SELECT * FROM (select * from orders order by created_at desc limit 1000) "
                + "yak_ops_preview LIMIT 20");
  }

  @Test
  void oraclePreviewUsesRowNumGuard() throws Exception {
    Connection opened = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    ResultSetMetaData metadata = mock(ResultSetMetaData.class);

    when(opened.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.getMetaData()).thenReturn(metadata);
    when(metadata.getColumnCount()).thenReturn(0);
    when(resultSet.next()).thenReturn(false);

    GenericJdbcCatalog catalog =
        new GenericJdbcCatalog(oracleConnection(), 5, 13) {
          @Override
          protected Connection openConnection() {
            return opened;
          }
        };

    catalog.preview(
        DataSourceCatalogReadRequest.sql(
            "select * from patient order by patient_id", Map.of()),
        20);

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(opened).prepareStatement(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue())
        .isEqualTo(
            "SELECT * FROM (select * from patient order by patient_id) "
                + "yak_ops_preview WHERE ROWNUM <= 20");
    verify(statement).setQueryTimeout(13);
    verify(statement).setMaxRows(20);
  }

  @Test
  void tableSearchPushesNormalizedPatternAndLimitToJdbcMetadata() throws Exception {
    Connection opened = mock(Connection.class);
    DatabaseMetaData metadata = mock(DatabaseMetaData.class);
    ResultSet resultSet = mock(ResultSet.class);
    when(opened.getMetaData()).thenReturn(metadata);
    when(metadata.storesLowerCaseIdentifiers()).thenReturn(true);
    when(metadata.getSearchStringEscape()).thenReturn("\\");
    when(metadata.getTables(eq("demo"), isNull(), eq("%orders%"), any(String[].class)))
        .thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true, true, false);
    when(resultSet.getString("TABLE_NAME")).thenReturn("orders");
    when(resultSet.getString("TABLE_CAT")).thenReturn("demo");
    when(resultSet.getString("TABLE_TYPE")).thenReturn("TABLE");

    GenericJdbcCatalog catalog =
        new GenericJdbcCatalog(connection(), 5, 13) {
          @Override
          protected Connection openConnection() {
            return opened;
          }
        };

    List<DataSourceTable> result =
        catalog.listTables(new DataSourceCatalogQuery(null, null, "Orders", 1));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getName()).isEqualTo("orders");
    verify(metadata).getTables(eq("demo"), isNull(), eq("%orders%"), any(String[].class));
  }

  @Test
  void oracleSearchUsesCurrentUserSchemaAndNoServiceNameCatalog() throws Exception {
    Connection opened = mock(Connection.class);
    DatabaseMetaData metadata = mock(DatabaseMetaData.class);
    ResultSet resultSet = mock(ResultSet.class);
    when(opened.getMetaData()).thenReturn(metadata);
    when(metadata.storesUpperCaseIdentifiers()).thenReturn(true);
    when(metadata.getTables(isNull(), eq("APP_USER"), eq("%PATIENT%"), any(String[].class)))
        .thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);

    GenericJdbcCatalog catalog =
        new GenericJdbcCatalog(oracleConnection(), 5, 13) {
          @Override
          protected Connection openConnection() {
            return opened;
          }
        };

    catalog.listTables(new DataSourceCatalogQuery(null, null, "patient", 100));

    verify(metadata)
        .getTables(isNull(), eq("APP_USER"), eq("%PATIENT%"), any(String[].class));
  }

  private JdbcConnectionProperties connection() {
    return new JdbcConnectionProperties(
        DataSourceDbType.MYSQL,
        "jdbc:mysql://localhost:3306/demo",
        "com.mysql.cj.jdbc.Driver",
        "test_user",
        null,
        "demo",
        null,
        Map.of(),
        "{}");
  }

  private JdbcConnectionProperties oracleConnection() {
    return new JdbcConnectionProperties(
        DataSourceDbType.ORACLE,
        "jdbc:oracle:thin:@//localhost:1521/ORCL",
        "oracle.jdbc.OracleDriver",
        "app_user",
        null,
        "ORCL",
        null,
        Map.of(),
        "{}");
  }
}
