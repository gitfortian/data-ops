package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.ops.business.mdm.domain.record.MdmRecordVersion;
import java.util.List;

/** Project-scoped persistence boundary for record version snapshots (R4). */
public interface MdmRecordVersionRepository {

  /** 幂等插入:同 (entity, master, version) 已存在则跳过,返回是否新插入。 */
  boolean insertIfAbsent(MdmRecordVersion version);

  List<MdmRecordVersion> listByMaster(Long entityId, String masterId);
}
