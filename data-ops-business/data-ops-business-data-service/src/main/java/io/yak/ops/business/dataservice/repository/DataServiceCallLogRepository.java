package io.yak.ops.business.dataservice.repository;

import io.yak.ops.business.dataservice.domain.InvocationRecord;
import java.time.LocalDateTime;
import java.util.List;

public interface DataServiceCallLogRepository {

  InvocationRecord save(InvocationRecord record);

  List<InvocationRecord> recent(int limit);

  List<InvocationRecord> recentByApi(Long apiId, int limit);

  List<InvocationRecord> between(LocalDateTime from, LocalDateTime to);
}
