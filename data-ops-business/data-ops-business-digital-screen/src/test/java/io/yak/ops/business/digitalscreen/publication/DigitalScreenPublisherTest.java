package io.yak.ops.business.digitalscreen.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenStatus;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenVersion;
import io.yak.ops.business.digitalscreen.repository.DigitalScreenRepository;
import io.yak.ops.business.digitalscreen.repository.DigitalScreenVersionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DigitalScreenPublisherTest {

  @Mock private DigitalScreenRepository screens;
  @Mock private DigitalScreenVersionRepository versions;

  private DigitalScreenPublisher publisher;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    publisher = new DigitalScreenPublisher(screens, versions);
  }

  @Test
  void publishAppendsImmutableSnapshotAndMovesPublishedPointer() {
    DigitalScreen draft = screen(DigitalScreenStatus.DRAFT, 3L, null, 0, Map.of());
    DigitalScreenVersion version = version(8L, 2, 3L, Map.of());
    DigitalScreen published = screen(DigitalScreenStatus.PUBLISHED, 3L, 3L, 2, Map.of());
    when(screens.lockById(7L)).thenReturn(draft);
    when(versions.nextVersionNo(7L)).thenReturn(2);
    when(versions.insert(eq(draft), eq(2), any(Instant.class))).thenReturn(version);
    when(screens.markPublished(eq(7L), eq(8L), eq(2), eq(3L), any(Instant.class)))
        .thenReturn(published);

    DigitalScreen result = publisher.publish(7L);

    assertThat(result.publishedVersionNo()).isEqualTo(2);
    verify(versions).insert(eq(draft), eq(2), any(Instant.class));
  }

  @Test
  void publishIsNoopWhenPublishedRevisionAlreadyMatchesDraft() {
    DigitalScreen current = screen(DigitalScreenStatus.PUBLISHED, 5L, 5L, 3, Map.of());
    when(screens.lockById(7L)).thenReturn(current);

    assertThat(publisher.publish(7L)).isSameAs(current);

    verify(versions, never()).nextVersionNo(7L);
    verify(versions, never()).insert(any(), anyInt(), any());
  }

  @Test
  void rollbackRestoresSnapshotThenPublishesAsNewAppendOnlyVersion() {
    Map<String, Object> oldBindings = Map.of("chart", Map.of("datasetId", "11"));
    DigitalScreen current = screen(DigitalScreenStatus.PUBLISHED, 9L, 8L, 3, Map.of());
    DigitalScreenVersion target = version(20L, 1, 2L, oldBindings);
    DigitalScreen restored = screen(DigitalScreenStatus.PUBLISHED, 10L, 8L, 3, oldBindings);
    DigitalScreenVersion rollbackVersion = version(23L, 4, 10L, oldBindings);
    DigitalScreen published = screen(DigitalScreenStatus.PUBLISHED, 10L, 10L, 4, oldBindings);
    when(screens.lockById(7L)).thenReturn(current);
    when(versions.findByVersionNo(7L, 1)).thenReturn(Optional.of(target));
    when(screens.restoreDraft(
        7L,
        target.name(),
        target.description(),
        target.templateId(),
        target.templateVersion(),
        target.bindings())).thenReturn(restored);
    when(versions.nextVersionNo(7L)).thenReturn(4);
    when(versions.insert(eq(restored), eq(4), any(Instant.class))).thenReturn(rollbackVersion);
    when(screens.markPublished(eq(7L), eq(23L), eq(4), eq(10L), any(Instant.class)))
        .thenReturn(published);

    DigitalScreen result = publisher.rollback(7L, 1);

    assertThat(result.publishedVersionNo()).isEqualTo(4);
    assertThat(result.publishedRevision()).isEqualTo(10L);
  }

  private DigitalScreen screen(
      DigitalScreenStatus status,
      long revision,
      Long publishedRevision,
      int publishedVersionNo,
      Map<String, Object> bindings) {
    Instant now = Instant.parse("2026-08-27T00:00:00Z");
    return new DigitalScreen(
        7L,
        "运营大屏",
        "大屏说明",
        "operation-center",
        1,
        status,
        bindings,
        revision,
        publishedRevision,
        publishedVersionNo > 0 ? 100L + publishedVersionNo : null,
        publishedVersionNo,
        publishedVersionNo > 0 ? now : null,
        now,
        now);
  }

  private DigitalScreenVersion version(
      long id,
      int versionNo,
      long sourceRevision,
      Map<String, Object> bindings) {
    Instant now = Instant.parse("2026-08-27T00:00:00Z");
    return new DigitalScreenVersion(
        id,
        7L,
        versionNo,
        sourceRevision,
        "运营大屏",
        "大屏说明",
        "operation-center",
        1,
        bindings,
        now,
        now);
  }
}
