package io.yak.ops.business.modeling.logical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Read-only version-pinned comparison. This is NOT logical-to-physical generation,
 * an approved mapping, a publish gate, or an executable SQL plan.
 */
@Service
@RequiredArgsConstructor
public class LogicalPhysicalHandoffPreviewService {
  private final LogicalDraftService logical;
  private final ModelRepository physicalModels;
  private final ModelStructureService structures;
  private final ProcessApi semantic;
  private final ObjectMapper json;

  public record ColumnCheck(String physicalColumn, Long physicalStdFieldId,
                            String logicalEntity, String logicalAttribute,
                            String result, String reason) {}
  public record Preview(Long logicalModelId, int logicalVersionNo, Long physicalModelId,
                        String physicalLayer, String physicalStatus, boolean readyForReview,
                        List<String> blockers, List<ColumnCheck> columns) {}

  public Preview preview(Long logicalId, int versionNo, Long physicalId) {
    if (logicalId == null || versionNo < 1 || physicalId == null) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "必须提供逻辑模型/版本和物理模型 ID");
    }
    // Always validate root ownership first. Never query raw snapshot IDs without project isolation.
    String snapshot = logical.versionSnapshot(logicalId, versionNo);
    Model target = physicalModels.findById(physicalId).orElseThrow(() ->
        new ModelingException(ModelingErrorCode.NOT_FOUND, "物理模型不存在或不属于当前项目"));
    StructureView structure = structures.get(physicalId);

    JsonNode root;
    try {
      root = json.readTree(snapshot);
      if (root == null || !root.isObject() || !root.path("model").path("id").isNumber()
          || root.path("model").path("id").asLong() != logicalId
          || !root.path("entities").isArray()) {
        throw new IllegalArgumentException("快照身份或结构不完整");
      }
    } catch (Exception ex) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "逻辑快照不可信，禁止生成物理映射候选");
    }

    List<String> blockers = new ArrayList<>();
    if ("ODS".equalsIgnoreCase(target.layerCode())) {
      blockers.add("ODS 是来源保真模型，不通过非 ODS 逻辑生成候选链路");
    }
    long processId = root.path("model").path("processId").asLong(-1);
    if (processId <= 0 || !Objects.equals(target.processId(), processId)) {
      blockers.add("物理模型和逻辑快照的业务过程身份不一致，需人工核对");
    }
    if (!"DRAFT".equals(target.status().name())) {
      blockers.add("目标物理模型不是 DRAFT，仅提供只读差异，不允许据此更新");
    }
    if (!"DRAFT".equals(root.path("model").path("status").asText())) {
      blockers.add("逻辑快照来源状态未知，无法确认其作者草稿身份");
    }

    Map<Long, List<JsonNode>> attrsByStd = new HashMap<>();
    Set<String> entityCodes = new HashSet<>();
    for (JsonNode item : root.path("entities")) {
      JsonNode entity = item.path("entity");
      String entityCode = entity.path("code").asText("");
      if (entityCode.isBlank() || !entityCodes.add(entityCode)
          || !item.path("attributes").isArray()) {
        blockers.add("冻结版本含无效或重复逻辑实体，请先修正并冻结新版本");
        continue;
      }
      for (JsonNode attribute : item.path("attributes")) {
        long id = attribute.path("stdFieldId").asLong(0);
        if (id > 0) attrsByStd.computeIfAbsent(id, ignored -> new ArrayList<>()).add(
            json.createObjectNode().put("entity", entity.path("name").asText(entityCode))
                .put("attribute", attribute.path("name").asText(""))
                .put("id", id));
      }
    }
    for (JsonNode relation : root.path("relations")) {
      if ("UNKNOWN".equals(relation.path("cardinality").asText())) {
        blockers.add("逻辑模型含未确认的实体关系基数，不能自动推导 JOIN");
        break;
      }
    }

    List<ColumnCheck> checks = new ArrayList<>();
    for (StructureView.ColumnView column : structure.columns()) {
      Long fieldId = column.stdFieldId();
      if (fieldId == null) {
        checks.add(new ColumnCheck(column.columnName(), null, null, null,
            "MISSING_STANDARD", "目标物理列尚无标准字段 ID；本预览不应用未批准的豁免"));
        continue;
      }
      StandardField standard = semantic.getField(fieldId);
      if (standard == null || !standard.isEnabled()) {
        checks.add(new ColumnCheck(column.columnName(), fieldId, null, null,
            "INVALID_STANDARD", "标准字段不存在、不可访问或已停用"));
        continue;
      }
      List<JsonNode> matches = attrsByStd.getOrDefault(fieldId, List.of());
      if (matches.size() != 1) {
        checks.add(new ColumnCheck(column.columnName(), fieldId, null, null,
            matches.isEmpty() ? "NO_LOGICAL_REFERENCE" : "AMBIGUOUS_LOGICAL_REFERENCE",
            matches.isEmpty() ? "冻结逻辑版本未使用此标准字段" : "多个逻辑属性使用同一标准字段，需人工指定归属"));
        continue;
      }
      JsonNode match = matches.get(0);
      checks.add(new ColumnCheck(column.columnName(), fieldId,
          match.path("entity").asText(), match.path("attribute").asText(),
          "CANDIDATE_ONLY", "标准字段 ID 一致；类型转换、粒度及业务键仍须人工审阅"));
    }
    if (checks.isEmpty()) blockers.add("目标物理模型未定义任何列");
    if (checks.stream().anyMatch(c -> !"CANDIDATE_ONLY".equals(c.result()))) {
      blockers.add("存在缺失/无效/歧义字段关联，不能形成受控交接");
    }
    // Never claim a published or executable handoff even if all references match.
    return new Preview(logicalId, versionNo, physicalId, target.layerCode(),
        target.status().name(), blockers.isEmpty(), List.copyOf(blockers), List.copyOf(checks));
  }
}
