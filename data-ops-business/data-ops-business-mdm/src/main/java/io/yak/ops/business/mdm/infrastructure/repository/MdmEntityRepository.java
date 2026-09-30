package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for the master data entity. */
public interface MdmEntityRepository {

  MdmEntity insert(MdmEntity entity, String operator);

  Optional<MdmEntity> findById(Long id);

  boolean existsByCode(String code);

  List<MdmEntity> findAll();

  PageData<MdmEntity> page(int pageNo, int pageSize, String keyword, MdmEntityStatus status);

  boolean update(MdmEntity entity);

  boolean changeStatus(Long id, MdmEntityStatus status);

  boolean hasReferences(Long id);

  boolean deleteById(Long id);
}
