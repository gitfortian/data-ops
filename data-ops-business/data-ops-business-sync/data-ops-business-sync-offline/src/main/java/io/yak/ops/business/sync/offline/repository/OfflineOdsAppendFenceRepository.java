package io.yak.ops.business.sync.offline.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineOdsAppendFenceMapper;
import io.yak.ops.business.sync.offline.dao.model.OfflineOdsAppendFencePO;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * Durable first-write-only fence. DB uniqueness serializes even separate Jobs
 * and workers. Until actual target commit receipts and overlapping window
 * accounting exist, ANY second APPEND to the same physical table is rejected.
 * This deliberately prevents regular repeat runs and is not full idempotency.
 */
@Repository
@ConditionalOnOfflineSyncEnabled
@RequiredArgsConstructor
public class OfflineOdsAppendFenceRepository {
  private static final ObjectMapper JSON = new ObjectMapper();

  private final OfflineOdsAppendFenceMapper mapper;
  private final CurrentProject currentProject;

  /** Must be invoked in the SAME offline-sync transaction as initial Batch insert. */
  public void claim(String frozenDefinition, long jobDefinitionId, BatchScope scope) {
    if (jobDefinitionId <= 0 || scope == null) {
      throw new IllegalArgumentException("ODS 写入预留必须有任务和冻结范围");
    }
    JsonNode root;
    try {
      root = JSON.readTree(frozenDefinition);
    } catch (Exception e) {
      throw new IllegalArgumentException("ODS 冻结定义不可解析", e);
    }
    if (root == null || !root.path("odsHandoff").isObject()) {
      throw new IllegalArgumentException("ODS 写入预留缺少完整来源绑定");
    }
    JsonNode handoff = root.path("odsHandoff");
    JsonNode sink = root.path("sink");
    JsonNode config = sink.path("config");
    String rawTable = firstText(config.path("targetTableName"),
        config.path("table"), sink.path("targetTableName"), sink.path("table"));
    String tableKey = normalizeTableKey(rawTable);
    long datasourceId = positive(sink.path("dataSourceId"), "targetDatasourceId");

    OfflineOdsAppendFencePO row = new OfflineOdsAppendFencePO();
    row.setProjectId(currentProject.requireProjectId());
    row.setTargetDatasourceId(datasourceId);
    row.setTargetTableKey(tableKey);
    row.setJobDefinitionId(jobDefinitionId);
    row.setModelId(positive(handoff.path("modelId"), "modelId"));
    row.setVersionNo(Math.toIntExact(positive(handoff.path("versionNo"), "versionNo")));
    row.setTargetObservationId(positive(handoff.path("targetObservationId"), "targetObservationId"));
    row.setScopeSha256(scope.fingerprint());
    row.setCreateTime(LocalDateTime.now());
    try {
      if (mapper.insert(row) != 1) {
        throw new IllegalStateException("ODS 目标写入预留未成功，不允许创建 Batch");
      }
    } catch (DataIntegrityViolationException duplicateOrInvalid) {
      throw new IllegalStateException(
          "ODS 目标表已有写入围栏，拒绝跨任务或跨 Batch 重复 APPEND；请先核验目标数据与提交回执",
          duplicateOrInvalid);
    }
  }

  /**
   * Conservative target identity: project + datasource + last physical table
   * segment. Different databases/schemas with the same table name intentionally
   * collide until a trusted DataSource-owned canonical namespace is available.
   * No client-chosen schema spelling can bypass the fence.
   */
  static String normalizeTableKey(String targetTable) {
    if (!StringUtils.hasText(targetTable)) {
      throw new IllegalArgumentException("ODS 目标表不能为空");
    }
    String[] parts = targetTable.trim().split("\\.", -1);
    if (parts.length > 3) throw new IllegalArgumentException("ODS 目标表限定为三段名称");
    for (String part : parts) {
      if (!part.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
        throw new IllegalArgumentException("ODS 目标表标识不合法，不能安全预留");
      }
    }
    return parts[parts.length - 1].toLowerCase(Locale.ROOT);
  }

  private static String firstText(JsonNode... values) {
    for (JsonNode value : values) {
      if (value.isTextual() && StringUtils.hasText(value.textValue())) {
        return value.textValue();
      }
    }
    throw new IllegalArgumentException("ODS 目标表缺失");
  }

  private static long positive(JsonNode node, String name) {
    String raw = node.asText("");
    if (!raw.matches("[1-9][0-9]{0,18}")) {
      throw new IllegalArgumentException(name + " 必须是正整数");
    }
    try {
      long value = Long.parseLong(raw);
      if (value > 0) return value;
    } catch (NumberFormatException ignored) {
      // Rejected below.
    }
    throw new IllegalArgumentException(name + " 超出支持范围");
  }
}
