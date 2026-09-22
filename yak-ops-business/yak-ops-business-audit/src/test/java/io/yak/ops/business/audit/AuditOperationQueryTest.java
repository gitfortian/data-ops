package io.yak.ops.business.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuditOperationQueryTest {

  @Test
  void normalizesPagingWhitespaceAndReversedTimeRange() {
    LocalDateTime later = LocalDateTime.of(2026, 9, 2, 12, 0);
    LocalDateTime earlier = LocalDateTime.of(2026, 9, 1, 12, 0);

    AuditOperationQuery query =
        new AuditOperationQuery(
            0,
            1000,
            "  offline  ",
            "  alice  ",
            -7L,
            "  OFFLINE_SYNC_RUN  ",
            "  OFFLINE_SYNC  ",
            "  FAILED  ",
            "  WEB  ",
            later,
            earlier);

    assertThat(query.page()).isEqualTo(1);
    assertThat(query.size()).isEqualTo(200);
    assertThat(query.keyword()).isEqualTo("offline");
    assertThat(query.actor()).isEqualTo("alice");
    assertThat(query.projectId()).isNull();
    assertThat(query.operationType()).isEqualTo("OFFLINE_SYNC_RUN");
    assertThat(query.startTime()).isEqualTo(earlier);
    assertThat(query.endTime()).isEqualTo(later);
  }
}
