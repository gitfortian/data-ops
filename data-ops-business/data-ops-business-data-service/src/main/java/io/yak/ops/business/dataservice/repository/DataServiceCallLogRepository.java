package io.yak.ops.business.dataservice.repository;

import io.yak.ops.business.dataservice.domain.InvocationRecord;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DataServiceCallLogRepository {

  InvocationRecord save(InvocationRecord record);

  List<InvocationRecord> recent(int limit);

  List<InvocationRecord> recentByApi(Long apiId, int limit);

  /** Exact persisted call audit in the trusted Project and owning API; no window fallback. */
  Optional<InvocationRecord> findByApiAndId(Long apiId, Long invocationId);

  List<InvocationRecord> between(LocalDateTime from, LocalDateTime to);
}
