package io.yak.ops.business.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditReadModelSnapshotTest {

  @Test
  void nullableJsonValuesRemainReadableAndSnapshotsStayImmutable() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("optional", null);
    AuditTimelineEvent event =
        new AuditTimelineEvent(
            1L,
            "TASK_SUBMITTED",
            "BUSINESS",
            "INFO",
            LocalDateTime.now(),
            "1",
            "OFFLINE_SYNC",
            "7",
            null,
            null,
            99L,
            null,
            "submitted",
            "任务已提交",
            null,
            payload);

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("optional", null);
    AuditOperationDetail detail = new AuditOperationDetail(null, metadata, List.of(event));

    assertThat(event.parentEventId()).isEqualTo(99L);
    assertThat(event.payload()).containsEntry("optional", null);
    assertThat(detail.metadata()).containsEntry("optional", null);
    assertThatThrownBy(() -> event.payload().put("new", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> detail.metadata().put("new", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
