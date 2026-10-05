package io.yak.ops.business.quality.api;

import java.math.BigDecimal;
import java.util.List;

/** Authorized current-definition metadata and pure candidate validation. Never runs or saves. */
public interface QualitySuggestionQueryApi {
  Context require(long monitorId);
  List<Candidate> validate(long monitorId, String expectedDefinition, List<Candidate> candidates);

  record Column(String name, String type, String remarks) {}
  record Template(long id, String code, String name, String type, String scope, String unit) {}
  record Context(long monitorId, String name, String tableName, String definition,
      boolean filtered, List<Column> columns, List<Template> templates, boolean truncated) {}
  record Candidate(long templateId, String name, String columnName, String operator,
      BigDecimal threshold, BigDecimal thresholdEnd, List<String> enumValues) {}
}
