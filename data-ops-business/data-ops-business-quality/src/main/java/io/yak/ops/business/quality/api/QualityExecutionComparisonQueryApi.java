package io.yak.ops.business.quality.api;

import java.time.LocalDateTime;
import java.util.List;

/** Bounded historical evidence only; matching recorded fields does not prove identical full definitions. */
public interface QualityExecutionComparisonQueryApi {
  Comparison compare(String baselineExecutionNo, String currentExecutionNo);

  record Comparison(Side baseline, Side current, List<Alignment> alignment) {}
  record Side(String executionNo, Long monitorId, String monitorName, String tableName,
      String executionStatus, String checkResult, int totalRules, int passedRules,
      int failedRules, int errorRules, LocalDateTime queuedAt, LocalDateTime finishedAt,
      List<Rule> rules, boolean truncated) {}
  record Rule(Long ruleId, String name, String templateCode, String ruleType, String columnName,
      String result, String metricValue, String expectedValue) {}
  record Alignment(Long ruleId, Integer baselineIndex, Integer currentIndex, boolean recordedDefinitionMatches) {}
}
