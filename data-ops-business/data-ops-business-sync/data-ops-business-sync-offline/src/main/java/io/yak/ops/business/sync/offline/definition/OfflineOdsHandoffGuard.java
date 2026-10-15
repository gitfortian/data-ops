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
    if (handoff == null || handoff.isNull()) return;
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
