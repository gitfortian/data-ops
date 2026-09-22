package io.yak.ops.business.mdm.domain.collect;

import java.time.LocalDateTime;

/**
 * 采集落地链路绑定(R1):一条已确认来源对应一个数据集成离线落地任务。
 * 只存反查锚点(jobDefinitionId/landingTable),执行态实时向 sync 反查,不冗余。
 */
public record MdmCollectLink(
    Long id,
    Long entityId,
    Long sourceId,
    Long datasourceId,
    String landingTable,
    Long jobDefinitionId,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public MdmCollectLink withPersisted(Long id, String operator, LocalDateTime time) {
    return new MdmCollectLink(
        id, entityId, sourceId, datasourceId, landingTable, jobDefinitionId, operator, time, time);
  }
}
