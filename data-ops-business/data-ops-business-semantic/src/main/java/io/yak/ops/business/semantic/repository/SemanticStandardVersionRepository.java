package io.yak.ops.business.semantic.repository;

import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.catalog.StandardVersion;
import java.util.List;

/** Project-scoped persistence boundary for standard version snapshots. */
public interface SemanticStandardVersionRepository {

  /** Records the full pre-change state of the standard (version = current). */
  void recordSnapshot(Standard standard, String operator);

  /** All snapshots of one standard, newest id first. */
  List<StandardVersion> listByStandard(Long standardId);
}
