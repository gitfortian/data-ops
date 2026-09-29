package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.ops.business.mdm.domain.distribution.MdmDistribution;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for the master data distribution config (ticket 58). */
public interface MdmDistributionRepository {

  MdmDistribution insert(MdmDistribution distribution, String operator);

  Optional<MdmDistribution> findById(Long id);

  List<MdmDistribution> listByEntity(Long entityId);

  boolean existsByTarget(Long entityId, String targetSystem, String mode, Long excludeId);

  boolean update(MdmDistribution distribution);

  boolean updateResult(MdmDistribution distribution);

  boolean deleteById(Long id);

  /** 按实体聚合:活跃配置数(服务端聚合,总览卡片用)。 */
  long countActiveByEntity(Long entityId);
}
