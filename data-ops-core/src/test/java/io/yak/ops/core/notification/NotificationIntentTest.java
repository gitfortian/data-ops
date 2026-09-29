package io.yak.ops.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NotificationIntentTest {

  @Test
  void normalizesCopyAndAcceptsOnlyInternalActions() {
    NotificationIntent intent = new NotificationIntent(
        7L,
        NotificationIntent.Type.TASK,
        NotificationIntent.Level.ERROR,
        "  Task failed  ",
        "  summary  ",
        "  detail  ",
        "  OFFLINE_SYNC_EXECUTION  ",
        "  99  ",
        "  /sync/batch-link-up/10/detail  ");

    assertThat(intent.title()).isEqualTo("Task failed");
    assertThat(intent.summary()).isEqualTo("summary");
    assertThat(intent.sourceType()).isEqualTo("OFFLINE_SYNC_EXECUTION");
    assertThat(intent.sourceId()).isEqualTo("99");
    assertThat(intent.actionPath()).isEqualTo("/sync/batch-link-up/10/detail");
  }

  @Test
  void rejectsInvalidProjectAndExternalActions() {
    assertThatThrownBy(() -> new NotificationIntent(
        0L,
        NotificationIntent.Type.TASK,
        NotificationIntent.Level.ERROR,
        "failed",
        null,
        null,
        "TASK",
        "1",
        "/tasks/1"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new NotificationIntent(
        7L,
        NotificationIntent.Type.TASK,
        NotificationIntent.Level.ERROR,
        "failed",
        null,
        null,
        "TASK",
        "1",
        "https://example.com"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
