package io.yak.ops.business.semantic.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.catalog.StandardVersion;
import io.yak.ops.business.semantic.dao.mapper.SemanticStandardVersionMapper;
import io.yak.ops.business.semantic.dao.model.SemanticStandardVersionPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for standard version snapshots. */
@Repository
@RequiredArgsConstructor
public class StandardVersionRepositoryAdapter implements SemanticStandardVersionRepository {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final SemanticStandardVersionMapper mapper;
  private final CurrentProject currentProject;

  @Override
  public void recordSnapshot(Standard standard, String operator) {
    SemanticStandardVersionPO po = new SemanticStandardVersionPO();
    po.setProjectId(requiredProjectId());
    po.setStandardId(standard.id());
    po.setVersion(standard.version());
    po.setPayloadJson(writePayload(standard));
    po.setOperatedBy(operator);
    po.setCreateTime(LocalDateTime.now());
    mapper.insert(po);
  }

  @Override
  public List<StandardVersion> listByStandard(Long standardId) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<SemanticStandardVersionPO>()
                .eq(SemanticStandardVersionPO::getProjectId, projectId)
                .eq(SemanticStandardVersionPO::getStandardId, standardId)
                .orderByDesc(SemanticStandardVersionPO::getId))
        .stream()
        .map(StandardVersionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<StandardVersion> listByCodeSet(String codeSetCode) {
    Long projectId = requiredProjectId();
    return mapper.selectList(new QueryWrapper<SemanticStandardVersionPO>()
            .select("id", "project_id", "standard_id", "version", "payload_json", "operated_by", "create_time")
            .eq("project_id", projectId)
            .eq("code_set_code", codeSetCode)
            .orderByDesc("id"))
        .stream()
        .map(StandardVersionRepositoryAdapter::toDomain)
        .toList();
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static StandardVersion toDomain(SemanticStandardVersionPO po) {
    return new StandardVersion(
        po.getStandardId(),
        po.getVersion(),
        po.getOperatedBy(),
        po.getCreateTime(),
        readPayload(po.getPayloadJson()));
  }

  private static String writePayload(Standard standard) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("kind", standard.kind().name());
    payload.put("code", standard.code());
    payload.put("name", standard.name());
    payload.put("status", standard.status().name());
    payload.put("version", standard.version());
    payload.put("sortOrder", standard.sortOrder());
    payload.put("preset", standard.preset());
    payload.put("description", standard.description());
    Standard.KindFields fields = standard.fields();
    payload.put("scope", fields.scope());
    payload.put("layer", fields.layer());
    payload.put("ruleExpr", fields.ruleExpr());
    payload.put("example", fields.example());
    payload.put("typeCode", fields.typeCode());
    payload.put("stdType", fields.stdType());
    payload.put("sourceMapping", fields.sourceMapping());
    payload.put("codeSetCode", fields.codeSetCode());
    payload.put("codeValue", fields.codeValue());
    payload.put("codeLabel", fields.codeLabel());
    payload.put("unitCode", fields.unitCode());
    payload.put("unitType", fields.unitType());
    payload.put("caliberCode", fields.caliberCode());
    payload.put("calRule", fields.calRule());
    payload.put("businessDesc", fields.businessDesc());
    payload.put("levelCode", fields.levelCode());
    payload.put("maskRule", fields.maskRule());
    try {
      return MAPPER.writeValueAsString(payload);
    } catch (Exception exception) {
      throw new IllegalStateException("serialize standard snapshot failed", exception);
    }
  }

  private static Map<String, Object> readPayload(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception exception) {
      return Map.of();
    }
  }
}
