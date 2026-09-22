package io.yak.ops.business.dataset.query;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetQueryRequest;
import io.yak.ops.business.dataset.DatasetQueryResult;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetVersion;
import java.util.List;

/** Runtime strategy per immutable Dataset source type. */
public interface DatasetSourceQueryAdapter {

  DatasetSourceType sourceType();

  ExecutionResult execute(
      Dataset dataset,
      DatasetVersion version,
      List<DatasetField> fields,
      DatasetQueryRequest request);

  record ExecutionResult(
      DatasetQueryResult result,
      String dataSourceId,
      String sql,
      long prepareMillis,
      long waitMillis,
      long executeMillis,
      long transferMillis) {}
}
