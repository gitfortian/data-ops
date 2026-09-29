package io.yak.ops.business.dataset;

import io.yak.ops.core.execution.sql.SqlExecutionColumn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result returned to Dashboard/Chart consumers by the Dataset query runtime. */
public record DatasetQueryResult(
    String queryId,
    long datasetId,
    long datasetVersionId,
    int datasetVersionNo,
    List<DatasetQueryColumnBinding> bindings,
    List<SqlExecutionColumn> columns,
    List<List<Object>> rows,
    int returnedRows,
    boolean truncated,
    long elapsedMillis) {

  public DatasetQueryResult {
    bindings = bindings == null ? List.of() : List.copyOf(bindings);
    columns = columns == null ? List.of() : List.copyOf(columns);
    rows = immutableRows(rows);
  }

  public DatasetQueryResult(
      long datasetId,
      long datasetVersionId,
      int datasetVersionNo,
      List<DatasetQueryColumnBinding> bindings,
      List<SqlExecutionColumn> columns,
      List<List<Object>> rows,
      int returnedRows,
      boolean truncated,
      long elapsedMillis) {
    this(
        null,
        datasetId,
        datasetVersionId,
        datasetVersionNo,
        bindings,
        columns,
        rows,
        returnedRows,
        truncated,
        elapsedMillis);
  }

  public DatasetQueryResult withQueryId(String value) {
    return new DatasetQueryResult(
        value,
        datasetId,
        datasetVersionId,
        datasetVersionNo,
        bindings,
        columns,
        rows,
        returnedRows,
        truncated,
        elapsedMillis);
  }

  private static List<List<Object>> immutableRows(List<List<Object>> values) {
    if (values == null || values.isEmpty()) return List.of();
    List<List<Object>> copied = new ArrayList<>(values.size());
    for (List<Object> row : values) {
      List<Object> cells = row == null ? new ArrayList<>() : new ArrayList<>(row);
      copied.add(Collections.unmodifiableList(cells));
    }
    return Collections.unmodifiableList(copied);
  }
}
