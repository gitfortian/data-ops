package io.yak.ops.business.sync.offline.schedule;

/** Schedule 子系统触发离线执行时使用的稳定边界。 */
public interface OfflineScheduleExecutionGateway {

  boolean hasOccupyingBatch(Long definitionId);

  Long submitScheduled(Long definitionId, String triggerToken);
}
