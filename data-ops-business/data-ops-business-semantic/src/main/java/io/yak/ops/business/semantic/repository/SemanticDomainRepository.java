package io.yak.ops.business.semantic.repository;

import io.yak.ops.business.semantic.api.BusinessDomain;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for the business domain tree. */
public interface SemanticDomainRepository {

  BusinessDomain insert(BusinessDomain domain, String operator);

  Optional<BusinessDomain> findById(Long id);

  boolean existsByCode(String code);

  List<BusinessDomain> findAll();

  boolean existsByParent(Long parentId);

  boolean update(BusinessDomain domain);

  boolean move(Long id, Long parentId, int sortOrder);

  boolean deleteById(Long id);
}
