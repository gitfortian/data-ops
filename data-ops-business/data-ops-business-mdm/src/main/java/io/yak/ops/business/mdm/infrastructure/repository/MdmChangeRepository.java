package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.mdm.domain.approval.MdmApprovalStatus;
import io.yak.ops.business.mdm.domain.approval.MdmChange;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for the master data change/approval (ticket 60). */
public interface MdmChangeRepository {

  MdmChange insert(MdmChange change);

  Optional<MdmChange> findById(Long id);

  PageData<MdmChange> page(
      Long entityId, String applicant, MdmApprovalStatus status, int pageNo, int pageSize);

  /** 按 master_id 列出变更历史(版本管理)。 */
  List<MdmChange> listByMaster(Long entityId, String masterId);

  /** 统计 PENDING 变更数(总览卡片)。 */
  long countPending(Long entityId);

  boolean update(MdmChange change);
}
