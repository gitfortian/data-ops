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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
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

  /** IDs, not labels, are the only permissible basis of a future explicit mapping. */
  public record ColumnCheck(String physicalColumn, Long physicalStdFieldId,
                            String logicalEntity, String logicalAttribute,
                            String result, String reason,
                            Long physicalColumnId, Long logicalEntityId, Long logicalAttributeId) {}
  /** Fingerprints bind this read-only preview to the precise input bytes. */
  public record Preview(Long logicalModelId, int logicalVersionNo, Long physicalModelId,
                        String physicalLayer, String physicalStatus, boolean readyForReview,
                        List<String> blockers, List<ColumnCheck> columns,
                        String logicalSnapshotSha256, String physicalStructureSha256) {}
  private record LogicalAttribute(Long entityId, String entityName, Long attributeId,
                                  String attributeName, Long stdFieldId) {}

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

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

    // Never pair evidence from different model identities, even when called from a
    // repository mock or a future implementation changes its projection contract.
    if (!Objects.equals(structure.modelId(), physicalId)) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN,
          "物理结构身份与选择的模型不一致，必须刷新后重新审阅");
    }
    final String physicalHash;
    try {
      physicalHash = sha256(json.writeValueAsBytes(structure));
    } catch (JsonProcessingException e) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN,
          "物理结构无法生成可复核指纹");
    }
    String logicalHash = sha256(snapshot.getBytes(StandardCharsets.UTF_8));

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

    Map<Long, List<LogicalAttribute>> attrsByStd = new HashMap<>();
    Map<Long, LogicalAttribute> allAttributes = new HashMap<>();
    Set<String> entityCodes = new HashSet<>();
    Set<Long> entityIds = new HashSet<>();
    for (JsonNode item : root.path("entities")) {
      JsonNode entity = item.path("entity");
      String entityCode = entity.path("code").asText("");
      long entityId = entity.path("id").asLong(0);
      if (entityId <= 0 || !entityIds.add(entityId) || entityCode.isBlank()
          || !entityCodes.add(entityCode) || !item.path("attributes").isArray()) {
        blockers.add("冻结版本含无效或重复逻辑实体，请先修正并冻结新版本");
        continue;
      }
      for (JsonNode attribute : item.path("attributes")) {
        long attrId = attribute.path("id").asLong(0);
        if (attrId <= 0 || attribute.path("entityId").asLong(0) != entityId
            || allAttributes.containsKey(attrId)) {
          blockers.add("冻结版本含无身份或跨实体逻辑属性，不能形成精确版本映射");
          continue;
        }
        long std = attribute.path("stdFieldId").asLong(0);
        LogicalAttribute a = new LogicalAttribute(entityId,
            entity.path("name").asText(entityCode), attrId,
            attribute.path("name").asText(""), std > 0 ? std : null);
        allAttributes.put(attrId, a);
        if (std > 0) attrsByStd.computeIfAbsent(std, ignored -> new ArrayList<>()).add(a);
      }
    }
    if (!root.path("relations").isArray()) {
      blockers.add("冻结版本缺失实体关系集合，不能判断关系基数");
    } else {
      for (JsonNode relation : root.path("relations")) {
        String cardinality = relation.path("cardinality").asText("");
        if (!Set.of("ONE_TO_ONE", "ONE_TO_MANY", "MANY_TO_ONE", "MANY_TO_MANY")
            .contains(cardinality)) {
          blockers.add("逻辑模型含未确认/无效的实体关系基数，不能自动推导 JOIN");
          break;
        }
        if (!entityIds.contains(relation.path("sourceEntityId").asLong(0))
            || !entityIds.contains(relation.path("targetEntityId").asLong(0))) {
          blockers.add("冻结关系引用不属于当前逻辑模型实体");
          break;
        }
      }
    }

    List<ColumnCheck> checks = new ArrayList<>();
    List<StructureView.ColumnView> columns = structure.columns() == null
        ? List.of() : structure.columns();
    Map<Long, Integer> physicalStdCount = new HashMap<>();
    Set<Long> physicalColumnIds = new HashSet<>();
    for (StructureView.ColumnView column : columns) {
      if (column.id() == null || column.id() <= 0 || !physicalColumnIds.add(column.id())) {
        blockers.add("物理列缺少唯一持久身份，不能保存映射");
      }
      if (column.stdFieldId() != null) {
        physicalStdCount.merge(column.stdFieldId(), 1, Integer::sum);
      }
    }
    Set<Long> representedAttributes = new HashSet<>();
    Map<Long, Boolean> validStandards = new HashMap<>();
    for (StructureView.ColumnView column : columns) {
      Long fieldId = column.stdFieldId();
      String result;
      String reason;
      LogicalAttribute matched = null;
      if (fieldId == null) {
        result = "MISSING_STANDARD";
        reason = "目标物理列尚无标准字段 ID；本预览不应用未批准的豁免";
      } else if (!validStandards.computeIfAbsent(fieldId, id -> {
        StandardField standard = semantic.getField(id);
        return standard != null && standard.isEnabled();
      })) {
        result = "INVALID_STANDARD";
        reason = "标准字段不存在、不可访问或已停用";
      } else {
        List<LogicalAttribute> matches = attrsByStd.getOrDefault(fieldId, List.of());
        if (physicalStdCount.getOrDefault(fieldId, 0) > 1) {
          result = "AMBIGUOUS_PHYSICAL_REFERENCE";
          reason = "多个物理列使用同一标准字段，必须由用户选择准确物理列";
        } else if (matches.size() != 1) {
          result = matches.isEmpty() ? "NO_LOGICAL_REFERENCE" : "AMBIGUOUS_LOGICAL_REFERENCE";
          reason = matches.isEmpty() ? "冻结逻辑版本未使用此标准字段"
              : "多个逻辑属性使用同一标准字段，需人工指定归属";
        } else {
          matched = matches.get(0);
          representedAttributes.add(matched.attributeId());
          result = "CANDIDATE_ONLY";
          reason = "标准字段 ID 一致；类型转换、粒度及业务键仍须人工审阅";
        }
      }
      checks.add(new ColumnCheck(column.columnName(), fieldId,
          matched == null ? null : matched.entityName(),
          matched == null ? null : matched.attributeName(), result, reason,
          column.id(), matched == null ? null : matched.entityId(),
          matched == null ? null : matched.attributeId()));
    }
    for (LogicalAttribute attribute : allAttributes.values().stream()
        .sorted(java.util.Comparator.comparing(LogicalAttribute::attributeId)).toList()) {
      if (representedAttributes.contains(attribute.attributeId())) continue;
      String result;
      String reason;
      if (attribute.stdFieldId() == null) {
        result = "MISSING_LOGICAL_STANDARD";
        reason = "冻结的逻辑属性未绑定标准字段，不能映射到非 ODS 业务列";
      } else if (!validStandards.computeIfAbsent(attribute.stdFieldId(), id -> {
        StandardField standard = semantic.getField(id);
        return standard != null && standard.isEnabled();
      })) {
        result = "INVALID_LOGICAL_STANDARD";
        reason = "逻辑属性引用的标准字段当前不可用或已停用";
      } else {
        result = "LOGICAL_ATTRIBUTE_UNMAPPED";
        reason = "没有唯一匹配的目标物理列；需明确删除、派生或用户指定的映射";
      }
      checks.add(new ColumnCheck(null, attribute.stdFieldId(),
          attribute.entityName(), attribute.attributeName(), result, reason,
          null, attribute.entityId(), attribute.attributeId()));
    }
    if (columns.isEmpty()) blockers.add("目标物理模型未定义任何列");
    if (allAttributes.isEmpty()) blockers.add("冻结逻辑版本缺少带持久身份的逻辑属性");
    if (checks.stream().anyMatch(c -> !"CANDIDATE_ONLY".equals(c.result()))) {
      blockers.add("存在缺失、失效、未覆盖或歧义的字段引用；仅允许人工修正后重新预检");
    }
    // A SHA is evidence of the compared input, NOT a saved mapping or physical
    // model version. Any subsequent confirmation must re-read both inputs.
    return new Preview(logicalId, versionNo, physicalId, target.layerCode(),
        target.status().name(), blockers.isEmpty(), List.copyOf(blockers), List.copyOf(checks),
        logicalHash, physicalHash);
  }
}
