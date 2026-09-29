package io.yak.ops.business.digitalscreen.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Persisted Digital Screen draft plus its current publication pointer. */
public record DigitalScreen(
    long id,
    String name,
    String description,
    String templateId,
    int templateVersion,
    DigitalScreenStatus status,
    Map<String, Object> bindings,
    long revision,
    Long publishedRevision,
    Long publishedVersionId,
    int publishedVersionNo,
    Instant publishedTime,
    Instant createTime,
    Instant updateTime) {

  public DigitalScreen {
    bindings = bindings == null
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
  }

  public boolean hasUnpublishedChanges() {
    return status == DigitalScreenStatus.PUBLISHED
        && !Objects.equals(publishedRevision, revision);
  }
}
