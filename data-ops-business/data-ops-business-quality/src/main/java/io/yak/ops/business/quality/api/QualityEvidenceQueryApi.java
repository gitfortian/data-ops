package io.yak.ops.business.quality.api;

import java.time.LocalDateTime;
import java.util.List;

/** Read-only execution evidence; contains neither SQL nor raw diagnostics/business samples. */
public interface QualityEvidenceQueryApi {
  ExecutionEvidence require(String executionNo);

  record ExecutionEvidence(String executionNo, String monitorName, String tableName,
      String executionStatus, String checkResult, int totalRules, int passedRules,
      int failedRules, int errorRules, LocalDateTime queuedAt, LocalDateTime finishedAt,
      List<RuleEvidence> rules, boolean truncated) {}

  record RuleEvidence(Long ruleId, String name, String columnName, String result,
      String metricValue, String expectedValue, Long durationMs, LocalDateTime recordedAt) {}
}
