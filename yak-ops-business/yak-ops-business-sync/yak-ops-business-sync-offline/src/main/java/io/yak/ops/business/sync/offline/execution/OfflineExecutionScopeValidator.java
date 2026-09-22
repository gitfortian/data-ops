package io.yak.ops.business.sync.offline.execution;

import io.yak.ops.business.sync.offline.domain.core.BatchScope;

/** Backfill 等子系统在物化 Batch 前使用的 execution scope 校验边界。 */
public interface OfflineExecutionScopeValidator {

  void validate(long taskId, String logicalJobSpecJson, BatchScope scope);
}
