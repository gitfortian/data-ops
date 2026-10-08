package io.yak.ops.business.dataservice.observability;

import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.dataservice.observability.InvocationEvidenceView;
import io.yak.ops.business.dataservice.repository.DataServiceCallLogRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceCallLogReader {
  private static final int DEFAULT_RECENT_LIMIT = 200;
  private final DataServiceCallLogRepository repository;

  public List<InvocationRecord> recent() {
    return repository.recent(DEFAULT_RECENT_LIMIT);
  }

  public List<InvocationRecord> recentByApi(Long apiId, int limit) {
    return repository.recentByApi(apiId, Math.max(1, Math.min(200, limit)));
  }

  /** Persisted successful calls only; ordinary recentByApi still includes failures. */
  public List<InvocationRecord> recentSuccessfulByApi(Long apiId, int limit) {
    return repository.recentSuccessfulByApi(apiId, Math.max(1, Math.min(200, limit)));
  }

  /**
   * Recovers successful persisted calls of exactly one immutable revision even
   * when newer revisions fill the normal product-level audit window.
   */
  public List<InvocationRecord> recentSuccessfulByApiAndRevision(
      Long apiId, Long sourceRevisionId, int limit) {
    return repository.recentSuccessfulByApiAndRevision(
        apiId, sourceRevisionId, Math.max(1, Math.min(200, limit)));
  }

  /**
   * Explicit bounded recovery reads retained persisted SUCCESS audits only.
   * No in-process fallback and no accidental cross-revision reconciliation.
   */
  public List<InvocationRecord> successfulPageByApiAndRevision(
      Long apiId, Long sourceRevisionId, Long beforeInvocationId, int limit) {
    if (apiId == null || apiId <= 0L || sourceRevisionId == null || sourceRevisionId <= 0L
        || (beforeInvocationId != null && beforeInvocationId <= 0L)) {
      throw new IllegalArgumentException("Data Service, immutable Revision and cursor must be positive");
    }
    return Objects.requireNonNull(
        repository.successfulPageByApiAndRevision(
            apiId, sourceRevisionId, beforeInvocationId, Math.max(1, Math.min(200, limit))),
        "Persisted Data Service successful invocation audit page is unavailable");
  }

  public InvocationEvidenceView findByApiAndId(Long apiId, Long invocationId) {
    if (apiId == null || apiId <= 0L || invocationId == null || invocationId <= 0L) {
      throw new IllegalArgumentException("数据服务 ID 与调用记录 ID 必须大于 0");
    }
    return repository.findByApiAndId(apiId, invocationId)
        .map(InvocationEvidenceView::found)
        .orElseGet(InvocationEvidenceView::notFound);
  }
}
