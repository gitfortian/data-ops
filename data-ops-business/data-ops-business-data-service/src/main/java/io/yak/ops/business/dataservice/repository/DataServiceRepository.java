package io.yak.ops.business.dataservice.repository;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import java.util.List;
import java.util.Optional;

public interface DataServiceRepository {

  /** Management-plane lookup scoped to the trusted CurrentProject. */
  Optional<DataServiceDefinition> findById(Long id);

  /** Management-plane path lookup scoped to the trusted CurrentProject. */
  Optional<DataServiceDefinition> findByPath(String path);

  /** Public invocation-plane lookup. Runtime paths remain globally unique. */
  Optional<DataServiceDefinition> findByRuntimePath(String path);

  /** Management-plane source lookup scoped to the trusted CurrentProject. */
  Optional<DataServiceDefinition> findBySource(String sourceType, String sourceRef);

  /** Management-plane catalog scoped to the trusted CurrentProject. */
  List<DataServiceDefinition> findAll();

  /**
   * Platform-wide aggregate count for the Home cockpit. This exposes only one scalar and must not
   * be generalized into a cross-Project definition/query API.
   */
  long count();

  /** Runtime paths are globally unique because the invocation URL has no Project namespace. */
  boolean existsByPath(String path, Long excludeId);

  DataServiceDefinition save(DataServiceDefinition definition);

  boolean delete(Long id);
}
