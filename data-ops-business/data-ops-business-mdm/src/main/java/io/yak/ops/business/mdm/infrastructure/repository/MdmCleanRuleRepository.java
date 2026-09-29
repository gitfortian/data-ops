package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.ops.business.mdm.domain.clean.MdmCleanRule;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleType;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for the master data cleansing rule. */
public interface MdmCleanRuleRepository {

  MdmCleanRule insert(MdmCleanRule rule, String operator);

  Optional<MdmCleanRule> findById(Long id);

  default List<MdmCleanRule> listByEntity(Long entityId) {
    return listByEntity(entityId, null);
  }

  /** ruleType=null 取全部类型；否则只取该类型（去重页不再被标准化/补全规则污染）。 */
  List<MdmCleanRule> listByEntity(Long entityId, MdmCleanRuleType ruleType);

  boolean existsByName(Long entityId, String ruleName, Long excludeId);

  boolean update(MdmCleanRule rule);

  boolean deleteById(Long id);
}
