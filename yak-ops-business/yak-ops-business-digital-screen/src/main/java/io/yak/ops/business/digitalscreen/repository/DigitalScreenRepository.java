package io.yak.ops.business.digitalscreen.repository;

import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence port for the mutable Digital Screen draft and publication pointer. */
public interface DigitalScreenRepository {

  List<DigitalScreen> list();

  /** Lightweight overview count; adapters should override to avoid decoding screen bindings. */
  default long count() {
    return list().size();
  }

  Optional<DigitalScreen> findById(long id);

  DigitalScreen lockById(long id);

  DigitalScreen insert(
      String name,
      String description,
      String templateId,
      int templateVersion,
      Map<String, Object> bindings);

  DigitalScreen update(
      long id,
      String name,
      String description,
      Map<String, Object> bindings);

  DigitalScreen restoreDraft(
      long id,
      String name,
      String description,
      String templateId,
      int templateVersion,
      Map<String, Object> bindings);

  DigitalScreen markPublished(
      long id,
      long versionId,
      int versionNo,
      long publishedRevision,
      Instant publishedTime);

  DigitalScreen offline(long id);

  boolean deleteById(long id);
}
