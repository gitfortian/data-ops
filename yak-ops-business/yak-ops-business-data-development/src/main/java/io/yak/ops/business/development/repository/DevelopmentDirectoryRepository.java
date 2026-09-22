package io.yak.ops.business.development.repository;

import io.yak.ops.business.development.domain.DevelopmentDirectory;
import java.util.List;
import java.util.Optional;

/** Durable directory repository for data-development organization metadata. */
public interface DevelopmentDirectoryRepository {

  DevelopmentDirectory insert(Long parentId, String name);

  Optional<DevelopmentDirectory> findById(Long id);

  List<DevelopmentDirectory> list();

  boolean existsByName(Long parentId, String name);

  boolean hasChildren(Long id);

  boolean updateName(Long id, String name);

  /** Moves a directory under a different parent (null or <= 0 means root). */
  boolean updateParentId(Long id, Long parentId);

  boolean deleteById(Long id);
}
