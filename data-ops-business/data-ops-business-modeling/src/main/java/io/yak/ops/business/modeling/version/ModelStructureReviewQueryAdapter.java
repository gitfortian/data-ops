package io.yak.ops.business.modeling.version;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.api.ModelStructureReviewQueryApi;
import io.yak.ops.business.modeling.repository.MappingRepository;
import io.yak.ops.business.modeling.repository.ModelVersionRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureFingerprint;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.security.ActionAuthorization;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ModelStructureReviewQueryAdapter implements ModelStructureReviewQueryApi {
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  private static final int STRUCTURE_LIMIT = 262144;
  private final ActionAuthorization authorization;
  private final CurrentProject currentProject;
  private final ModelStructureService structures;
  private final ModelVersionRepository versions;
  private final MappingRepository mappings;

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Context read(long modelId, int baselineVersionNo, String expectedDefinition) {
    authorization.requirePermission(ModelingPermissionCode.READ);
    Long projectId = currentProject.requireProjectId();
    if (modelId <= 0 || baselineVersionNo <= 0 || (expectedDefinition != null && !expectedDefinition.matches("[a-f0-9]{64}"))) throw invalid();
    // Existing structure/mapping commands acquire this same model lock before changing saved inputs.
    StructureView saved = structures.getForUpdate(modelId);
    if (saved == null || !Objects.equals(saved.modelId(), modelId)) throw invalid();
    boundedEncode(saved, STRUCTURE_LIMIT);
    var baseline = versions.findByVersionNo(modelId, baselineVersionNo).orElseThrow(ModelStructureReviewQueryAdapter::invalid);
    if (baseline.id() == null || baseline.id() <= 0 || !Objects.equals(baseline.modelId(), modelId)
        || baseline.versionNo() != baselineVersionNo || baseline.structureJson() == null
        || baseline.structureJson().length() > STRUCTURE_LIMIT) throw invalid();
    StructureView before;
    try { before = JSON.readValue(baseline.structureJson(), StructureView.class); }
    catch (JsonProcessingException corrupt) { throw invalid(); }
    if (before == null || (before.modelId() != null && !Objects.equals(before.modelId(), modelId))) throw invalid();
    var changes = ModelStructureReviewProjection.changes(before, saved);
    var rows = mappings.listByModelForReview(modelId);
    if (rows == null || rows.size() > MAPPING_LIMIT) throw invalid();
    var ids = new java.util.HashSet<Long>();
    var signatures = new java.util.ArrayList<MappingSignature>();
    for (var row : rows) {
      if (row == null || row.getId() == null || row.getId() <= 0 || !ids.add(row.getId())
          || !Objects.equals(row.getProjectId(), projectId) || !Objects.equals(row.getModelId(), modelId)) throw invalid();
      signatures.add(new MappingSignature(row.getId(), ModelStructureReviewProjection.text(row.getTargetColumn(), 128),
          row.getSourceDatasourceId(), ModelStructureReviewProjection.text(row.getSourceDatabase(), 128),
          ModelStructureReviewProjection.text(row.getSourceTable(), 128), ModelStructureReviewProjection.text(row.getSourceColumn(), 128),
          ModelStructureReviewProjection.text(row.getTransformExpr(), 1024), row.getStdProcessFieldId()));
    }
    String definition = digest(StructureFingerprint.of(saved) + ":" + baseline.id() + ":" + digest(baseline.structureJson())
        + ":" + digest(boundedEncode(signatures, STRUCTURE_LIMIT)));
    if (expectedDefinition != null && !definition.equals(expectedDefinition)) {
      throw new IllegalArgumentException("已保存结构、基准版本或当前映射已变化，请返回原版本页重新准备比较");
    }
    var result = new Context(projectId.toString(), Long.toString(modelId), baselineVersionNo, baseline.id().toString(), definition,
        before.columns().size(), saved.columns().size(), changes, ModelStructureReviewProjection.checks(saved, rows, changes), ModelStructureReviewProjection.GAPS);
    boundedEncode(result, PAYLOAD_LIMIT);
    return result;
  }

  private record MappingSignature(Long id, String target, Long datasourceId, String database, String table,
      String column, String transform, Long standardFieldId) {}

  private static String boundedEncode(Object value, int limit) {
    try {
      String encoded = JSON.writeValueAsString(value);
      if (encoded.length() > limit) throw invalid();
      return encoded;
    } catch (JsonProcessingException corrupt) { throw invalid(); }
  }

  private static String digest(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("无法核验比较输入"); }
  }

  private static IllegalArgumentException invalid() { return new IllegalArgumentException("指定版本、已保存结构或当前映射不可用/超出支持范围，请在原模型页重新核对"); }
}
