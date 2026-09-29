package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.ops.business.mdm.domain.clean.MdmMergeLog;
import java.util.List;

/** Project-scoped persistence boundary for the master data merge log (append-only). */
public interface MdmMergeLogRepository {

  MdmMergeLog insert(MdmMergeLog log, String operator);

  List<MdmMergeLog> listByEntity(Long entityId);
}
