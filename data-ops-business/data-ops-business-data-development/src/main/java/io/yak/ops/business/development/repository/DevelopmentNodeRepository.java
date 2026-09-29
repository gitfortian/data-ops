package io.yak.ops.business.development.repository;

import io.yak.ops.business.development.domain.DevelopmentNode;
import java.util.List;
import java.util.Optional;

/** Durable repository for data-development tree node metadata. */
public interface DevelopmentNodeRepository {

  /**
   * Compatibility contract for pre-Stage-5A callers. Production persistence must not trust the
   * supplied projectId; it is retained only to avoid a wide source-compatibility break.
   */
  DevelopmentNode insert(
      String name,
      String type,
      Long ignoredProjectId,
      Long directoryId,
      boolean configured);

  /** Creates a Project Root whose ownership is resolved by the persistence adapter. */
  default DevelopmentNode insert(
      String name,
      String type,
      Long directoryId,
      boolean configured) {
    return insert(name, type, null, directoryId, configured);
  }

  Optional<DevelopmentNode> findById(Long id);

  List<DevelopmentNode> list();

  /** Lightweight count for overview/read-model callers; adapters should override when possible. */
  default long count() {
    return list().size();
  }

  boolean existsByName(Long directoryId, String name);

  boolean existsInDirectory(Long directoryId);

  boolean updateName(Long id, String name);

  boolean updateConfigured(Long id, boolean configured);

  /** Stores the last editor without forcing all existing repository test doubles to implement it. */
  default boolean updateUpdatedBy(Long id, String updatedBy) {
    return false;
  }

  /** Moves the node to a different directory (null or <= 0 means root). */
  boolean updateDirectoryId(Long id, Long directoryId);

  boolean deleteById(Long id);
}
