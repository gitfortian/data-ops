package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.ops.business.mdm.dao.MdmOverviewCardRow;
import io.yak.ops.business.mdm.dao.MdmOverviewTotalsRow;
import java.util.List;

/**
 * Project-scoped read boundary for the overview surface. Aggregation happens in
 * SQL (bounded by contract): never a full entity list pulled into the JVM.
 */
public interface MdmOverviewRepository {

  /** 项目内六卡 + 管线节点计数(单条聚合 SQL)。 */
  MdmOverviewTotalsRow totals();

  /** 最近更新的 limit 个实体及其计数(单条 SQL,limit 有界)。 */
  List<MdmOverviewCardRow> recentCards(int limit);
}
