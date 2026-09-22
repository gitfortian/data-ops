package io.yak.ops.business.dataservice.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.dataservice.repository.DataServiceCallLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataServiceCallLogReaderTest {

  @Test
  void readsOnlyTheRequestedServiceWithBoundedLimit() {
    DataServiceCallLogRepository repository = mock(DataServiceCallLogRepository.class);
    InvocationRecord record = new InvocationRecord(
        1L, 7L, "Orders", "/orders", "PUBLIC", null, null, null,
        "{}", true, 10L, 1, null, LocalDateTime.now());
    when(repository.recentByApi(7L, 200)).thenReturn(List.of(record));

    DataServiceCallLogReader reader = new DataServiceCallLogReader(repository);

    assertThat(reader.recentByApi(7L, 999)).containsExactly(record);
    verify(repository).recentByApi(7L, 200);
  }
}
