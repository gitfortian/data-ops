package io.yak.ops.business.sync.offline.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.core.modeling.OdsSyncHandoffVerifier;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Offline owner verifies optional ODS provenance, never a client-supplied "ready" flag. */
@Component
@ConditionalOnOfflineSyncEnabled
@RequiredArgsConstructor
public class OfflineOdsHandoffGuard {
  private final CurrentProject currentProject;
  private final ObjectProvider<OdsSyncHandoffVerifier> verifier;
  private static final ObjectMapper JSON = new ObjectMapper();

  /** Interpret JSON structure, never raw substring matching: escaped field names
   * (for example \\u006f) must not bypass the protected ODS handoff gate. */
  public static boolean requiresVerification(String frozenDefinitionJson) {
    if (!StringUtils.hasText(frozenDefinitionJson)) return false;
    try {
      JsonNode root = JSON.readTree(frozenDefinitionJson);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("离线同步任务定义必须为 JSON 对象");
      }
      // Presence is a protected intent: explicit null must fail closed, not silently
      // downgrade an ODS-linked definition into an unlinked ordinary job.
      return root.has("odsHandoff");
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalArgumentException("离线同步任务定义 JSON 无法解析，禁止跳过 ODS 来源核验",
          exception);
    }
  }

  /**
   * APPEND is not replay-safe without an external committed-window/idempotency
   * receipt. Reject fresh Retry/Backfill attempts until that contract exists.
   */
  public static void rejectUnsafeAppendReplay(String frozenDefinitionJson) {
    if (requiresVerification(frozenDefinitionJson)) {
      throw new IllegalStateException(
          "ODS APPEND 暂不允许 Retry/Backfill 自动重放；须先核验目标已提交范围并建立幂等写入合同");
    }
  }

  /**
   * Publish/execute from the immutable JobRevision/Batch snapshot, not a mutable
   * Task row. If the snapshot cannot resolve an exact single-table endpoint,
   * refuse the provenance claim rather than inferring its schema.
   */
  public void verifyFrozen(String frozenDefinitionJson) {
    JsonNode root;
    try {
      root = JSON.readTree(frozenDefinitionJson);
    } catch (Exception e) {
      throw new IllegalArgumentException("离线任务的冻结定义不可解析", e);
    }
    if (root == null || !root.isObject()) {
      throw new IllegalArgumentException("无效离线任务冻结定义");
    }
    if (!root.has("odsHandoff")) return;
    if (root.path("odsHandoff").isNull()) {
      throw new IllegalArgumentException("ODS 交接引用不能为 null");
    }
    if (!"GUIDE_SINGLE".equalsIgnoreCase(root.path("basic").path("mode").asText())) {
      throw new IllegalArgumentException("ODS 交接当前只支持确切的单表同步，不允许多表或 SQL 模式猜测");
    }
    JsonNode source = root.path("source");
    JsonNode sink = root.path("sink");
    JsonNode sinkConfig = sink.path("config");
    // ODS deployment is an operator-controlled DDL step. A sync task must
    // never silently create its target or perform unreviewed destructive writes.
    if (sinkConfig.path("autoCreateTable").asBoolean(false)
        || sink.path("autoCreateTable").asBoolean(false)) {
      throw new IllegalArgumentException("ODS 同步禁止自动建表；必须先人工执行 DDL 并核验目标 Catalog");
    }
    String writeMode = sinkConfig.path("writeMode").asText("APPEND");
    String outerWriteMode = sink.path("writeMode").asText("APPEND");
    if (!"APPEND".equalsIgnoreCase(writeMode)
        || !"APPEND".equalsIgnoreCase(outerWriteMode)) {
      throw new IllegalArgumentException("ODS 同步暂仅允许 APPEND；覆盖/更新须先有独立写入冲突合同");
    }
    Long sourceId = endpointId(source);
    Long sinkId = endpointId(sink);
    String sourceTable = endpointTable(source);
    String sinkTable = endpointTable(sink);
    verify(frozenDefinitionJson, sourceId, sourceTable, sinkId, sinkTable);
  }

  private static Long endpointId(JsonNode endpoint) {
    String id = endpoint.path("dataSourceId").asText("");
    if (!id.matches("[1-9][0-9]{0,18}")) {
      throw new IllegalArgumentException("ODS 交接端点必须保留可信数据源 ID");
    }
    try { return Long.valueOf(id); }
    catch (NumberFormatException ex) {
      throw new IllegalArgumentException("无效数据源 ID", ex);
    }
  }

  private static String endpointTable(JsonNode endpoint) {
    JsonNode config = endpoint.path("config");
    String value = config.path("targetTableName").asText("");
    if (!StringUtils.hasText(value)) value = config.path("table").asText("");
    if (!StringUtils.hasText(value)) value = endpoint.path("targetTableName").asText("");
    if (!StringUtils.hasText(value)) value = endpoint.path("table").asText("");
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("ODS 交接需要可精确核验的单表端点");
    }
    return value;
  }

  /** Applies to the frozen definition, NOT the editable UI navigation state. */
  public void verify(String definitionJson, Long sourceId, String sourceTable,
                     Long sinkId, String sinkTable) {
    if (!StringUtils.hasText(definitionJson)) return;
    JsonNode root;
    try {
      root = JSON.readTree(definitionJson);
    } catch (Exception e) {
      throw new IllegalArgumentException("离线任务定义 JSON 无法用于 ODS 交接核验", e);
    }
    if (root == null || !root.isObject()) throw new IllegalArgumentException("无效离线任务定义");
    JsonNode handoff = root.get("odsHandoff");
    if (handoff == null) return;
    if (handoff.isNull()) {
      throw new IllegalArgumentException("ODS 交接引用不能为 null");
    }
    if (!handoff.isObject() || handoff.size() != 3) {
      throw new IllegalArgumentException("ODS 交接必须提供完整的模型、版本及目标观察 ID");
    }
    long modelId = positiveLong(handoff, "modelId");
    int versionNo = Math.toIntExact(positiveLong(handoff, "versionNo"));
    long observationId = positiveLong(handoff, "targetObservationId");
    if (sourceId == null || sinkId == null || !StringUtils.hasText(sourceTable)
        || !StringUtils.hasText(sinkTable)) {
      throw new IllegalArgumentException("ODS 交接的同步任务缺少来源或目标表的真实配置");
    }
    var owner = verifier.getIfAvailable();
    if (owner == null) {
      throw new IllegalStateException("Modeling Owner 不可用，拒绝未经核验的 ODS 同步交接");
    }
    // Modeling checks source and destination ownership + current published
    // version + target observation + fresh Catalog. Never trust this JSON alone.
    owner.verify(currentProject.requireProjectId(), modelId, versionNo, observationId,
        sourceId, sourceTable, sinkId, sinkTable);
  }

  private static long positiveLong(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
        || value.longValue() <= 0) {
      throw new IllegalArgumentException("ODS 交接 " + field + " 必须为正整数");
    }
    return value.longValue();
  }
}
