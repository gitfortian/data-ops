package io.yak.ops.business.digitalscreen.publication;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenStatus;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenVersion;
import io.yak.ops.business.digitalscreen.repository.DigitalScreenRepository;
import io.yak.ops.business.digitalscreen.repository.DigitalScreenVersionRepository;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns publish/offline/rollback transitions while keeping version history append-only. */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DigitalScreenPublisher {

  private final DigitalScreenRepository screens;
  private final DigitalScreenVersionRepository versions;

  @Transactional
  public DigitalScreen publish(long screenId) {
    DigitalScreen draft = screens.lockById(screenId);
    if (draft.status() == DigitalScreenStatus.PUBLISHED
        && draft.publishedVersionId() != null
        && Objects.equals(draft.publishedRevision(), draft.revision())) {
      return draft;
    }

    Instant now = Instant.now();
    int versionNo = versions.nextVersionNo(screenId);
    DigitalScreenVersion version = versions.insert(draft, versionNo, now);
    return screens.markPublished(
        screenId,
        version.id(),
        version.versionNo(),
        draft.revision(),
        now);
  }

  @Transactional
  public DigitalScreen offline(long screenId) {
    screens.lockById(screenId);
    return screens.offline(screenId);
  }

  @Transactional
  public DigitalScreen rollback(long screenId, int versionNo) {
    screens.lockById(screenId);
    DigitalScreenVersion target = versions.findByVersionNo(screenId, versionNo)
        .orElseThrow(() -> versionNotFound(screenId, versionNo));

    DigitalScreen restored = screens.restoreDraft(
        screenId,
        target.name(),
        target.description(),
        target.templateId(),
        target.templateVersion(),
        target.bindings());

    Instant now = Instant.now();
    int nextVersionNo = versions.nextVersionNo(screenId);
    DigitalScreenVersion rollbackVersion = versions.insert(restored, nextVersionNo, now);
    return screens.markPublished(
        screenId,
        rollbackVersion.id(),
        rollbackVersion.versionNo(),
        restored.revision(),
        now);
  }

  private IllegalArgumentException versionNotFound(long screenId, int versionNo) {
    return new IllegalArgumentException(
        "数字化大屏版本不存在：" + screenId + " / V" + versionNo);
  }
}
