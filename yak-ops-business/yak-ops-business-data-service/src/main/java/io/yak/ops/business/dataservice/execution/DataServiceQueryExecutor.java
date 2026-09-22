package io.yak.ops.business.dataservice.execution;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceQueryResponse;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionColumn;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Executes already-compiled read-only Data Service SQL through the shared SQL runtime. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceQueryExecutor {

  private final SqlExecutionRuntime sqlExecutionRuntime;

  public DataServiceQueryResponse execute(
      DataServiceDefinition definition,
      DataServiceSqlCompiler.CompiledSql compiled,
      DataServicePagination pagination) {
    int maxRows = definition.settings().maxRows();
    int fetchRows = maxRows;
    if (pagination != null && !pagination.returnTotalNum()) {
      long requiredRows = pagination.offset() + pagination.pageSize();
      fetchRows = (int) Math.min(maxRows, Math.max(1L, requiredRows));
    }
    SqlExecutionResult result = sqlExecutionRuntime.execute(new SqlExecutionRequest(
        String.valueOf(definition.runtimeSnapshot().dataSourceId()),
        compiled.sql(), compiled.parameters(), fetchRows, definition.settings().timeoutSeconds(),
        SqlExecutionContext.of(SqlExecutionCaller.DATA_SERVICE, String.valueOf(definition.id()))));
    if (!result.resultSet()) throw new IllegalStateException("数据服务仅允许返回 SELECT 查询结果");
    return response(result, pagination);
  }

  private DataServiceQueryResponse response(SqlExecutionResult result, DataServicePagination pagination) {
    List<String> columns = result.columns().stream().map(SqlExecutionColumn::label).toList();
    List<Map<String, Object>> allRows = new ArrayList<>(result.rows().size());
    for (List<Object> values : result.rows()) {
      Map<String, Object> row = new LinkedHashMap<>();
      for (int index = 0; index < columns.size(); index++) {
        row.put(columns.get(index), index < values.size() ? values.get(index) : null);
      }
      allRows.add(row);
    }
    if (pagination == null) {
      return new DataServiceQueryResponse(
          columns, allRows, result.truncated(), allRows.size(), result.timing().totalMillis());
    }
    int from = (int) Math.min(pagination.offset(), allRows.size());
    int to = Math.min(allRows.size(), from + pagination.pageSize());
    List<Map<String, Object>> pageRows = List.copyOf(allRows.subList(from, to));
    Integer totalNum = pagination.returnTotalNum() ? allRows.size() : null;
    return new DataServiceQueryResponse(
        columns, pageRows, result.truncated(), pageRows.size(), result.timing().totalMillis(), totalNum,
        pagination.pageNum(), pagination.pageSize());
  }
}
