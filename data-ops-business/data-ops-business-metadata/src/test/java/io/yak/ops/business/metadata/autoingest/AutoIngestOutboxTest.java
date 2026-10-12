package io.yak.ops.business.metadata.autoingest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutoIngestOutboxTest {
  @Test
  void emitsOneDurableFactForEachCommittedTableIdentity() {
    AutoIngestEventMapper mapper = mock(AutoIngestEventMapper.class);
    when(mapper.enqueue(eq(42L), eq(7L), any(), any())).thenReturn(1);
    new AutoIngestOutbox(mapper).enqueueCommittedTables(42L, 7L,
        new LinkedHashMap<>(Map.of("db.public.orders", 13L, "db.public.items", 14L)));
    verify(mapper).enqueue(eq(42L), eq(7L), eq(13L), any());
    verify(mapper).enqueue(eq(42L), eq(7L), eq(14L), any());
    verifyNoMoreInteractions(mapper);
  }

  @Test
  void rejectsUncommittedOrUnidentifiedEvidenceInsteadOfDroppingTicket() {
    AutoIngestEventMapper mapper = mock(AutoIngestEventMapper.class);
    AutoIngestOutbox queue = new AutoIngestOutbox(mapper);
    assertThatThrownBy(() -> queue.enqueueCommittedTables(null, 7L, Map.of("table", 1L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> queue.enqueueCommittedTables(42L, 7L, Map.of("table", 0L)))
        .isInstanceOf(IllegalStateException.class);
    when(mapper.enqueue(eq(42L), eq(7L), eq(1L), any())).thenReturn(0);
    assertThatThrownBy(() -> queue.enqueueCommittedTables(42L, 7L, Map.of("table", 1L)))
        .isInstanceOf(IllegalStateException.class);
  }
}
