package io.yak.ops.business.mdm.domain.record;

import java.time.LocalDateTime;

/** 主数据记录(统一主数据表,只读;由主数据加工任务在数据开发侧写入)。 */
public record MdmRecord(
    Long id,
    Long entityId,
    String masterId,
    String attributes,
    String sourceIds,
    MdmRecordStatus status,
    int version,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  /** 合并后主记录:吸收 attributes/source_ids,version 递增,保持 ACTIVE。 */
  public MdmRecord mergedAsMaster(String attributes, String sourceIds) {
    return new MdmRecord(
        id, entityId, masterId, attributes, sourceIds, MdmRecordStatus.ACTIVE, version + 1,
        createTime, updateTime);
  }

  /** 合并后让位记录:标记 MERGED(保留可追溯),version 递增。 */
  public MdmRecord mergedAway() {
    return new MdmRecord(
        id, entityId, masterId, attributes, sourceIds, MdmRecordStatus.MERGED, version + 1,
        createTime, updateTime);
  }

  /** 清洗后记录:attributes 更新,version 递增,保持 ACTIVE(标准化/补全,ticket 57)。 */
  public MdmRecord cleaned(String attributes) {
    return new MdmRecord(
        id, entityId, masterId, attributes, sourceIds, MdmRecordStatus.ACTIVE, version + 1,
        createTime, updateTime);
  }

  /** 审批通过后应用变更:更新 attributes/status,version 递增(ticket 60)。 */
  public MdmRecord appliedChange(String attributes, MdmRecordStatus status) {
    return new MdmRecord(
        id, entityId, masterId, attributes, sourceIds, status, version + 1,
        createTime, updateTime);
  }
}
