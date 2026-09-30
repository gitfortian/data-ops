package io.yak.ops.business.semantic.repository;

import io.yak.ops.business.semantic.api.WarehouseLayer;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for warehouse layer config. */
public interface SemanticLayerRepository {

  WarehouseLayer insert(WarehouseLayer layer, String operator);

  Optional<WarehouseLayer> findById(Long id);

  boolean existsByCode(String code);

  List<WarehouseLayer> list();

  /** 当前项目最大排序号(无行时 0);新建分层默认排序 = max+1(2026-09-16)。 */
  int maxSortOrder();

  boolean update(WarehouseLayer layer);

  boolean changeStatus(Long id, String status);

  boolean deleteById(Long id);

  /** Number of project layers bound to the given naming standard. */
  long countByNamingStandard(Long standardId);
}
