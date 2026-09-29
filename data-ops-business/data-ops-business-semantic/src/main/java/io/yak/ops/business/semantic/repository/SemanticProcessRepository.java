package io.yak.ops.business.semantic.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.semantic.api.BusinessProcess;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for business processes. */
public interface SemanticProcessRepository {

  BusinessProcess insert(BusinessProcess process, String operator);

  Optional<BusinessProcess> findById(Long id);

  boolean existsByCode(String code);

  PageData<BusinessProcess> page(
      int pageNo, int pageSize, Long domainId, String keyword, String bizType);

  List<BusinessProcess> listByDomain(Long domainId);

  boolean existsByDomain(Long domainId);

  boolean update(BusinessProcess process);

  boolean deleteById(Long id);
}
