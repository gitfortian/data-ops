package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.ops.business.mdm.domain.clean.MdmDedupIgnore;
import java.util.List;
import java.util.Optional;

/**
 * Project-scoped persistence boundary for the dedup "ignore group" ledger
 * (reuse-plan R7). Ignored keys are excluded inside the dedup discovery SQL,
 * so this is the only place that decides what "已忽略" means.
 */
public interface MdmDedupIgnoreRepository {

  /** 同一规则下同一组键只有一条;已存在时调用方按幂等处理,不重复登记。 */
  Optional<MdmDedupIgnore> findByKey(Long entityId, Long ruleId, String matchKey);

  MdmDedupIgnore insert(MdmDedupIgnore ignore, String operator);

  List<MdmDedupIgnore> listByRule(Long entityId, Long ruleId);

  boolean delete(Long id);

  /** 删除去重规则时一并清账:规则没了,它的忽略键永远不会再被命中。 */
  void deleteByRule(Long ruleId);
}
