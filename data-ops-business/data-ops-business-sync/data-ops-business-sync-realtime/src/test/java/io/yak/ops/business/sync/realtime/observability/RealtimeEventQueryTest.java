package io.yak.ops.business.sync.realtime.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RealtimeEventQueryTest {

  @Test
  void readsEventsOnlyForExistingTask() {
    RealtimeJobStore store = mock(RealtimeJobStore.class);
    RealtimeEventQuery query = new RealtimeEventQuery(store);
    when(store.definition(7L)).thenReturn(Optional.of(definition()));
    when(store.events(7L)).thenReturn(List.of());

    assertThat(query.events(7L)).isEmpty();

    verify(store).events(7L);
  }

  @Test
  void rejectsUnknownTaskBeforeReadingEvents() {
    RealtimeJobStore store = mock(RealtimeJobStore.class);
    RealtimeEventQuery query = new RealtimeEventQuery(store);
    when(store.definition(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> query.events(7L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("实时同步任务不存在");
  }

  private DefinitionRow definition() {
    LocalDateTime now = LocalDateTime.now();
    return new DefinitionRow(
        7L,
        "test-job",
        null,
        null,
        3L,
        "PUBLISHED",
        "STOPPED",
        "STOPPED",
        1,
        1,
        "digest",
        null,
        now,
        now);
  }
}
