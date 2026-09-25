package io.yak.ops.business.dataset.query;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryRequest;
import io.yak.ops.business.dataset.DatasetQueryResult;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import io.yak.ops.business.dataset.DatasetQuerySubject;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.observability.DatasetQueryPerformanceRecorder;
import io.yak.ops.business.dataset.query.DatasetSourceQueryAdapter.ExecutionResult;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.core.execution.sql.SqlExecutionPolicyViolationException;
import io.yak.ops.core.project.ProjectContextException;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.core.security.ActionAuthorization;
import io.yak.ops.core.security.ActionPermissionCodes;
import java.net.SocketTimeoutException;
import java.sql.SQLTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/** Resolves the exact DatasetVersion and records one terminal diagnostic trace per attempt. */
@Component
public class DatasetQueryCoordinator {

  private final DatasetRepository repository;
  private final DatasetSourceQueryRegistry sourceRegistry;
  private final DatasetQueryPerformanceRecorder performanceRecorder;
  private final ActionAuthorization actionAuthorization;

  public DatasetQueryCoordinator(
      DatasetRepository repository,
      DatasetSourceQueryRegistry sourceRegistry,
      DatasetQueryPerformanceRecorder performanceRecorder,
      ActionAuthorization actionAuthorization) {
    this.repository = repository;
    this.sourceRegistry = sourceRegistry;
    this.performanceRecorder = performanceRecorder;
    this.actionAuthorization = actionAuthorization;
  }

  public DatasetQueryResult query(long datasetId, DatasetQueryRequest request) {
    return query(datasetId, request, null);
  }

  public DatasetQueryResult query(
      long datasetId, DatasetQueryRequest request, DatasetQuerySubject subject) {
    String queryId = UUID.randomUUID().toString().replace("-", "");
    Instant startedAt = Instant.now();
    long queryStartedAt = System.nanoTime();
    String stage = "AUTHORIZE_ACTION";
    Dataset dataset = null;
    DatasetVersion version = null;
    String dataSourceId = null;
    String sql = null;
    long servicePrepareMillis = 0L;

    try {
      actionAuthorization.requirePermission(ActionPermissionCodes.DATASET_QUERY);

      stage = "VALIDATE_REQUEST";
      if (datasetId <= 0L) {
        throw new IllegalArgumentException("datasetId 必须大于 0");
      }

      stage = "RESOLVE_DATASET";
      dataset = repository.findDataset(datasetId)
          .orElseThrow(() -> new IllegalArgumentException("Dataset 不存在：" + datasetId));
      if (dataset.status() != DatasetStatus.ONLINE) {
        throw new IllegalArgumentException("只有 ONLINE Dataset 可以查询：" + datasetId);
      }

      stage = "RESOLVE_VERSION";
      version = resolveVersion(dataset, request == null ? null : request.versionNo());
      dataSourceId = version.dataSourceId();
      sql = version.sql();

      stage = "RESOLVE_FIELDS";
      List<DatasetField> fields = repository.listFields(version.id());
      stage = "RESOLVE_ADAPTER";
      DatasetSourceQueryAdapter adapter = sourceRegistry.require(version.sourceType());
      servicePrepareMillis = elapsedMillis(queryStartedAt);

      stage = "EXECUTE_SOURCE";
      ExecutionResult execution = adapter.execute(dataset, version, fields, request);
      long totalMillis = elapsedMillis(queryStartedAt);
      DatasetQueryResult result = execution.result().withQueryId(queryId);
      performanceRecorder.record(trace(
          queryId, datasetId, dataset, version, execution.dataSourceId(), execution.sql(),
          DatasetQueryStatus.SUCCESS, null, null, null, subject,
          execution.waitMillis(), servicePrepareMillis + execution.prepareMillis(),
          execution.executeMillis(), execution.transferMillis(), totalMillis,
          result.returnedRows(), result.truncated(), startedAt));
      return result;
    } catch (RuntimeException exception) {
      long totalMillis = elapsedMillis(queryStartedAt);
      performanceRecorder.record(trace(
          queryId, datasetId, dataset, version, dataSourceId, sql, classify(exception), stage,
          exception.getClass().getSimpleName(), exception.getMessage(), subject,
          0L, servicePrepareMillis, 0L, 0L, totalMillis, 0, false, startedAt));
      throw exception;
    }
  }

  private DatasetQueryPerformance trace(
      String queryId,
      long requestedDatasetId,
      Dataset dataset,
      DatasetVersion version,
      String dataSourceId,
      String sql,
      DatasetQueryStatus status,
      String failureStage,
      String errorType,
      String errorMessage,
      DatasetQuerySubject subject,
      long waitMillis,
      long prepareMillis,
      long executeMillis,
      long transferMillis,
      long totalMillis,
      int returnedRows,
      boolean truncated,
      Instant startedAt) {
    return new DatasetQueryPerformance(
        queryId,
        dataset == null ? requestedDatasetId : dataset.id(),
        dataset == null ? null : dataset.name(),
        version == null ? null : version.id(),
        version == null ? null : version.versionNo(),
        version == null ? null : version.sourceType().name(),
        dataSourceId,
        sql,
        null,
        status,
        failureStage,
        errorType,
        errorMessage,
        subject == null ? null : subject.subjectType(),
        subject == null ? null : subject.sourceDomain(),
        subject == null ? null : subject.sourceIdentity(),
        subject == null ? null : subject.displayHint(),
        waitMillis,
        prepareMillis,
        executeMillis,
        transferMillis,
        totalMillis,
        returnedRows,
        truncated,
        startedAt,
        Instant.now());
  }

  private DatasetVersion resolveVersion(Dataset dataset, Integer versionNo) {
    if (versionNo == null) {
      if (dataset.currentVersionId() == null) {
        throw new IllegalStateException("Dataset 尚未建立当前版本：" + dataset.id());
      }
      return repository.findVersion(dataset.currentVersionId())
          .orElseThrow(() -> new IllegalStateException(
              "Dataset 当前版本不存在：" + dataset.currentVersionId()));
    }
    if (versionNo <= 0) {
      throw new IllegalArgumentException("versionNo 必须大于 0");
    }
    return repository.findVersion(dataset.id(), versionNo)
        .orElseThrow(() -> new IllegalArgumentException(
            "DatasetVersion 不存在：datasetId=" + dataset.id() + ", versionNo=" + versionNo));
  }

  private DatasetQueryStatus classify(RuntimeException exception) {
    if (isTimeout(exception)) return DatasetQueryStatus.TIMEOUT;
    if (exception instanceof IllegalArgumentException
        || exception instanceof SqlExecutionPolicyViolationException
        || exception instanceof ProjectContextException
        || exception instanceof ActionAccessDeniedException) {
      return DatasetQueryStatus.REJECTED;
    }
    return DatasetQueryStatus.FAILED;
  }

  private boolean isTimeout(Throwable throwable) {
    Throwable current = throwable;
    for (int depth = 0; current != null && depth < 12; depth++) {
      if (current instanceof SQLTimeoutException
          || current instanceof SocketTimeoutException
          || current instanceof TimeoutException
          || current.getClass().getSimpleName().toLowerCase().contains("timeout")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static long elapsedMillis(long startedAt) {
    return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
  }
}
