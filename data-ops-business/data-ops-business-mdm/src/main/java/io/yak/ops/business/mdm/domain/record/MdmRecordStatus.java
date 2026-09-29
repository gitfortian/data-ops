package io.yak.ops.business.mdm.domain.record;

/** 主数据记录状态:ACTIVE 对外可见 / MERGED 合并后保留(可追溯) / DELETED 软删除。 */
public enum MdmRecordStatus {
  ACTIVE,
  MERGED,
  DELETED
}
