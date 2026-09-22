package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for the master data attribute. */
public interface MdmAttributeRepository {

  MdmAttribute insert(MdmAttribute attribute, String operator);

  Optional<MdmAttribute> findById(Long id);

  List<MdmAttribute> listByEntity(Long entityId);

  boolean existsByCode(Long entityId, String code);

  boolean existsPk(Long entityId);

  boolean update(MdmAttribute attribute);

  boolean deleteById(Long id);
}
