package io.yak.ops.business.quality.domain;

import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.TriggerType;
import java.time.LocalDateTime;

/** 业务查询条件；Repository 不依赖 HTTP DTO。 */
public final class QualityQuery {
  private QualityQuery() {}

  public record Template(String keyword, String dimension, RuleScope scope) {}

  public record Monitor(
      int current, int pageSize, String keyword, Long dataSourceId,
      String databaseName, boolean databaseFilter, String schemaName, boolean schemaFilter,
      String tableName, Boolean enabled, CheckResult lastResult) {}

  public record TableAsset(
      int current, int pageSize, long dataSourceId,
      String databaseName, boolean databaseFilter, String schemaName, boolean schemaFilter,
      String keyword) {}

  public record Execution(
      int current, int pageSize, String keyword, Long monitorId,
      ExecutionStatus executionStatus, CheckResult checkResult) {}

  public record CustomTemplate(String keyword, String dimension, Long folderId, boolean folderFilter) {}

  public record ExecutionWorkspace(
      int current, int pageSize, String keyword, String objectKeyword,
      Long dataSourceId, Long monitorId, ExecutionStatus executionStatus,
      CheckResult checkResult, TriggerType triggerType, Boolean hasIssues,
      String dimension, RuleScope scope, LocalDateTime queuedAfter, LocalDateTime queuedBefore) {}
}
