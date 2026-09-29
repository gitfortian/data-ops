package io.yak.ops.business.digitalscreen.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable snapshot created whenever a Digital Screen draft is published. */
public record DigitalScreenVersion(
    long id,
    long screenId,
    int versionNo,
    long sourceRevision,
    String name,
    String description,
    String templateId,
    int templateVersion,
    Map<String, Object> bindings,
    Instant publishedTime,
    Instant createTime) {

  public DigitalScreenVersion {
    bindings = bindings == null
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
  }
}
