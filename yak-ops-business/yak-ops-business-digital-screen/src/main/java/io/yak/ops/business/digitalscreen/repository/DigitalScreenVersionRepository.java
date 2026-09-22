package io.yak.ops.business.digitalscreen.repository;

import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Append-only persistence port for immutable published Digital Screen snapshots. */
public interface DigitalScreenVersionRepository {

  List<DigitalScreenVersion> list(long screenId);

  Optional<DigitalScreenVersion> findById(long versionId);

  Optional<DigitalScreenVersion> findByVersionNo(long screenId, int versionNo);

  int nextVersionNo(long screenId);

  DigitalScreenVersion insert(DigitalScreen draft, int versionNo, Instant publishedTime);

  void deleteByScreenId(long screenId);
}
