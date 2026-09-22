package io.yak.ops.business.mdm.domain.distribution;

import java.time.LocalDateTime;

/**
 * 主数据分发配置(ticket 58):实体级,目标系统+方式+频率。
 * 最小化设计:API 方式复用 data-service,MESSAGE/FILE 为占位(后续增量)。
 */
public record MdmDistribution(
    Long id,
    Long entityId,
    String targetSystem,
    String targetName,
    MdmDistributionMode mode,
    String frequency,
    String scope,
    MdmDistributionStatus status,
    LocalDateTime lastDistributeTime,
    Integer lastDistributeCount,
    Integer lastDistributeFail,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public MdmDistribution withPersisted(Long id, String operator, LocalDateTime now) {
    return new MdmDistribution(
        id, entityId, targetSystem, targetName, mode, frequency, scope, status,
        lastDistributeTime, lastDistributeCount, lastDistributeFail,
        operator, now, now);
  }

  public MdmDistribution withEditable(
      String targetName, MdmDistributionMode mode, String frequency, String scope) {
    return new MdmDistribution(
        id, entityId, targetSystem,
        targetName == null ? this.targetName : targetName,
        mode == null ? this.mode : mode,
        frequency == null ? this.frequency : frequency,
        scope == null ? this.scope : scope,
        status, lastDistributeTime, lastDistributeCount, lastDistributeFail,
        createdBy, createTime, updateTime);
  }

  public MdmDistribution withStatus(MdmDistributionStatus newStatus) {
    return new MdmDistribution(
        id, entityId, targetSystem, targetName, mode, frequency, scope, newStatus,
        lastDistributeTime, lastDistributeCount, lastDistributeFail,
        createdBy, createTime, updateTime);
  }

  public MdmDistribution withResult(LocalDateTime time, int count, int failCount) {
    return new MdmDistribution(
        id, entityId, targetSystem, targetName, mode, frequency, scope, status,
        time, count, failCount, createdBy, createTime, updateTime);
  }
}
