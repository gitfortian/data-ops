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
    LocalDateTime updateTime,
    String attributeOverrides) {

  /** Compatibility constructor for callers that predate source-protected attributes. */
  public MdmRecord(
      Long id,
      Long entityId,
      String masterId,
      String attributes,
      String sourceIds,
      MdmRecordStatus status,
      int version,
      LocalDateTime createTime,
      LocalDateTime updateTime) {
    this(id, entityId, masterId, attributes, sourceIds, status, version,
        createTime, updateTime, "{}");
  }

  /** 合并后主记录:吸收 attributes/source_ids,version 递增,保持 ACTIVE。 */
  public MdmRecord mergedAsMaster(String attributes, String sourceIds) {
    return new MdmRecord(
        id, entityId, masterId, attributes, sourceIds, MdmRecordStatus.ACTIVE, version + 1,
        createTime, updateTime, attributeOverrides);
  }

  /** Preserve the merge decision when an absorbed field came from another source record. */
  public MdmRecord mergedAsMaster(
      String attributes, String sourceIds, String attributeOverrides) {
    return new MdmRecord(
        id, entityId, masterId, attributes, sourceIds, MdmRecordStatus.ACTIVE, version + 1,
        createTime, updateTime, attributeOverrides);
  }

  /** 合并后让位记录:标记 MERGED(保留可追溯),version 递增。 */
  public MdmRecord mergedAway() {
    return new MdmRecord(
        id, entityId, masterId, attributes, sourceIds, MdmRecordStatus.MERGED, version + 1,
        createTime, updateTime, attributeOverrides);
  }

  /** 清洗后记录:attributes 更新,version 递增,保持 ACTIVE(标准化/补全,ticket 57)。 */
  public MdmRecord cleaned(String attributes) {
    return new MdmRecord(
        id, entityId, masterId, attributes, sourceIds, MdmRecordStatus.ACTIVE, version + 1,
        createTime, updateTime, attributeOverrides);
  }

  /** 审批通过后应用变更:更新 attributes/status,version 递增(ticket 60)。 */
  public MdmRecord appliedChange(String attributes, MdmRecordStatus status) {
    return new MdmRecord(
        id, entityId, masterId, attributes, sourceIds, status, version + 1,
        createTime, updateTime, attributeOverrides);
  }

  /** Governance change updates effective values and their source-override metadata together. */
  public MdmRecord appliedChange(
      String attributes, MdmRecordStatus status, String attributeOverrides) {
    return new MdmRecord(
        id, entityId, masterId, attributes, sourceIds, status, version + 1,
        createTime, updateTime, attributeOverrides);
  }

  /** Cleansing is an explicit data-owner action and must survive a later source refresh. */
  public MdmRecord cleaned(String attributes, String attributeOverrides) {
    return new MdmRecord(
        id, entityId, masterId, attributes, sourceIds, MdmRecordStatus.ACTIVE, version + 1,
        createTime, updateTime, attributeOverrides);
  }
}
