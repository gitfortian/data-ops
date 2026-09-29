package io.yak.ops.business.dataservice.observability;

import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.dataservice.repository.DataServiceCallLogRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
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
}
