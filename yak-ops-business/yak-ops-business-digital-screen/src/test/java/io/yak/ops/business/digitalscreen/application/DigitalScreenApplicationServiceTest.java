package io.yak.ops.business.digitalscreen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenStatus;
import io.yak.ops.business.digitalscreen.publication.DigitalScreenPublisher;
import io.yak.ops.business.digitalscreen.repository.DigitalScreenRepository;
import io.yak.ops.business.digitalscreen.repository.DigitalScreenVersionRepository;
import io.yak.ops.business.digitalscreen.version.DigitalScreenVersionReader;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DigitalScreenApplicationServiceTest {

  @Mock private DigitalScreenRepository repository;
  @Mock private DigitalScreenVersionRepository versionRepository;
  @Mock private DigitalScreenPublisher publisher;
  @Mock private DigitalScreenVersionReader versionReader;

  private DigitalScreenApplicationService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new DigitalScreenApplicationService(
        repository,
        versionRepository,
        publisher,
        versionReader);
  }

  @Test
  void createNormalizesDefinitionAndStartsFromDraftPersistence() {
    DigitalScreen created = screen(1L, "运营大屏", DigitalScreenStatus.DRAFT, 1L, null, 0);
    when(repository.insert("运营大屏", null, "operation-center", 1, Map.of()))
        .thenReturn(created);

    DigitalScreen result = service.create(new CreateDigitalScreenCommand(
        "  运营大屏  ",
        "   ",
        " operation-center ",
        null));

    assertThat(result).isSameAs(created);
    verify(repository).insert("运营大屏", null, "operation-center", 1, Map.of());
  }

  @Test
  void duplicateCopiesDraftButNeverPublicationHistory() {
    Map<String, Object> bindings = Map.of("metric-1", Map.of("datasetId", "12"));
    DigitalScreen source = screen(
        9L,
        "院区运营大屏",
        DigitalScreenStatus.PUBLISHED,
        4L,
        3L,
        2,
        bindings);
    DigitalScreen copy = screen(
        10L,
        "院区运营大屏 - 副本",
        DigitalScreenStatus.DRAFT,
        1L,
        null,
        0,
        bindings);
    when(repository.findById(9L)).thenReturn(Optional.of(source));
    when(repository.insert(
        "院区运营大屏 - 副本",
        source.description(),
        source.templateId(),
        source.templateVersion(),
        source.bindings())).thenReturn(copy);

    DigitalScreen result = service.duplicate(9L);

    assertThat(result.status()).isEqualTo(DigitalScreenStatus.DRAFT);
    assertThat(result.publishedVersionNo()).isZero();
    verify(repository).insert(
        "院区运营大屏 - 副本",
        source.description(),
        source.templateId(),
        source.templateVersion(),
        source.bindings());
  }

  @Test
  void deleteRemovesImmutableHistoryBeforeScreenIdentity() {
    DigitalScreen existing = screen(12L, "待删除", DigitalScreenStatus.PUBLISHED, 2L, 2L, 1);
    when(repository.findById(12L)).thenReturn(Optional.of(existing));
    when(repository.deleteById(12L)).thenReturn(true);

    service.delete(12L);

    verify(versionRepository).deleteByScreenId(12L);
    verify(repository).deleteById(12L);
  }

  private DigitalScreen screen(
      long id,
      String name,
      DigitalScreenStatus status,
      long revision,
      Long publishedRevision,
      int publishedVersionNo) {
    return screen(id, name, status, revision, publishedRevision, publishedVersionNo, Map.of());
  }

  private DigitalScreen screen(
      long id,
      String name,
      DigitalScreenStatus status,
      long revision,
      Long publishedRevision,
      int publishedVersionNo,
      Map<String, Object> bindings) {
    Instant now = Instant.parse("2026-08-27T00:00:00Z");
    return new DigitalScreen(
        id,
        name,
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
}
