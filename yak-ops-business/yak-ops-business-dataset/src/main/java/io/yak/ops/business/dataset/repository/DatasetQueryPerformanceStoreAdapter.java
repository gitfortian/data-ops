package io.yak.ops.business.dataset.repository;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.dao.model.DatasetQueryPerformancePO;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MySQL-backed Dataset query diagnostics shared by all application instances. */
@Repository
@RequiredArgsConstructor
public class DatasetQueryPerformanceStoreAdapter implements DatasetQueryPerformanceStore {

  private final DatasetDao datasetDao;

  @Override
  public void append(Long projectId, DatasetQueryPerformance trace) {
    DatasetQueryPerformancePO row = toPo(projectId, trace);
    if (datasetDao.insertQueryPerformance(row) != 1) {
      throw new IllegalStateException("保存 Dataset 查询诊断记录失败：" + trace.queryId());
    }
  }

  @Override
  public List<DatasetQueryPerformance> recent(
      Long projectId,
      Set<Long> datasetIds,
      Set<String> queryIds,
      Set<DatasetQueryStatus> statuses,
      Long minTotalMillis,
      int requestedLimit) {
    List<String> statusNames = statuses == null
        ? List.of()
        : statuses.stream().map(Enum::name).toList();
    return datasetDao.selectQueryPerformance(
            projectId, datasetIds, queryIds, statusNames, minTotalMillis, requestedLimit)
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public int deleteBefore(Instant cutoff, int requestedLimit) {
    return datasetDao.deleteQueryPerformanceBefore(cutoff, requestedLimit);
  }

  private DatasetQueryPerformancePO toPo(Long projectId, DatasetQueryPerformance trace) {
    DatasetQueryPerformancePO row = new DatasetQueryPerformancePO();
    row.setProjectId(projectId);
    row.setQueryId(trace.queryId());
    row.setDatasetId(trace.datasetId());
    row.setDatasetName(trace.datasetName());
    row.setDatasetVersionId(trace.datasetVersionId());
    row.setDatasetVersionNo(trace.datasetVersionNo());
    row.setSourceType(trace.sourceType());
    row.setDataSourceId(trace.dataSourceId());
    row.setSqlPreview(trace.sql());
    row.setSqlHash(trace.sqlHash());
    row.setStatus(trace.status().name());
    row.setFailureStage(trace.failureStage());
    row.setErrorType(trace.errorType());
    row.setErrorMessage(trace.errorMessage());
    row.setWaitMillis(trace.waitMillis());
    row.setPrepareMillis(trace.prepareMillis());
    row.setExecuteMillis(trace.executeMillis());
    row.setTransferMillis(trace.transferMillis());
    row.setTotalMillis(trace.totalMillis());
    row.setReturnedRows(trace.returnedRows());
    row.setTruncated(trace.truncated());
    row.setStartedAt(timestamp(trace.startedAt()));
    row.setFinishedAt(timestamp(trace.finishedAt()));
    return row;
  }

  private DatasetQueryPerformance toDomain(DatasetQueryPerformancePO row) {
    return new DatasetQueryPerformance(
        row.getQueryId(),
        row.getDatasetId() == null ? 0L : row.getDatasetId(),
        row.getDatasetName(),
        row.getDatasetVersionId(),
        row.getDatasetVersionNo(),
        row.getSourceType(),
        row.getDataSourceId(),
        row.getSqlPreview(),
        row.getSqlHash(),
        DatasetQueryStatus.valueOf(row.getStatus()),
        row.getFailureStage(),
        row.getErrorType(),
        row.getErrorMessage(),
        value(row.getWaitMillis()),
        value(row.getPrepareMillis()),
        value(row.getExecuteMillis()),
        value(row.getTransferMillis()),
        value(row.getTotalMillis()),
        row.getReturnedRows() == null ? 0 : row.getReturnedRows(),
        Boolean.TRUE.equals(row.getTruncated()),
        instant(row.getStartedAt()),
        instant(row.getFinishedAt()));
  }

  private long value(Long value) {
    return value == null ? 0L : value;
  }

  private Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
