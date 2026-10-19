package io.yak.ops.business.modeling.standard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * B2 review-only candidate for the future Semantic-owned publication SPI.
 *
 * <p>Deliberately not a Spring bean, and NOT wired to any publish endpoint.
 * DR-06/PD-010 Semantic, Modeling and Security owner acceptance is required
 * before using any result to permit/deny production publication. The proposed
 * consistency token is descriptive evidence, not proof of cross-domain
 * transactional atomicity.
 */
public final class DraftPublicationEvidenceVerifier {
  public static final String PROPOSED_ALGORITHM = "STANDARD_PUBLICATION_PROPOSAL_V1";

  private DraftPublicationEvidenceVerifier() {}

  public enum Verdict {
    VALID_CANDIDATE, BLOCKED, UNKNOWN, ODS_OUT_OF_SCOPE
  }

  public record ColumnRequest(String columnName, String physicalType, Long standardFieldId) {}

  public record Request(long projectId, long modelId, String layerCode, String physicalStructureSha256,
                        List<ColumnRequest> columns) {}

  /**
   * Only non-sensitive standard digests and IDs cross the boundary.
   * Semantic remains responsible for permissions, role/type compatibility,
   * applicable TYPE/UNIT/CALIBER/CODE/SECURITY evidence and digest schemas.
   */
  public record ColumnFact(String columnName, String physicalType, Long standardFieldId,
                           String standardVerdict, String standardEvidenceSha256,
                           String codeSetStatus, Integer codeSetItemCount,
                           Integer codeSetEnabledCount, String codeSetSha256) {}

  public record Observation(long projectId, long modelId, String layerCode, String physicalStructureSha256,
                            String verdict, String digestAlgorithmId,
                            String semanticEvidenceSha256, String consistencyToken,
                            List<ColumnFact> columns) {}

  public record FrozenCandidate(long projectId, long modelId, String layerCode, String structureSha256,
                                String semanticDigestAlgorithm, String semanticEvidenceSha256,
                                String consistencyToken, String candidateDigestSha256,
                                List<ColumnFact> columns) {}

  public record Evaluation(Verdict verdict, String reason, FrozenCandidate snapshot) {
    static Evaluation reject(Verdict verdict, String reason) {
      return new Evaluation(verdict, reason, null);
    }
  }

  public static Evaluation evaluate(Request request, Observation observation) {
    if (request == null || request.projectId() <= 0 || request.modelId() <= 0
        || !digest(request.physicalStructureSha256())
        || request.columns() == null || request.columns().isEmpty()) {
      return Evaluation.reject(Verdict.UNKNOWN, "物理结构或列全集身份不可验证");
    }
    if ("ODS".equals(request.layerCode())) {
      return Evaluation.reject(Verdict.ODS_OUT_OF_SCOPE, "ODS 贴源例外由 Modeling 原有合同独立处理");
    }
    if (request.layerCode() == null
        || !Set.of("DWD", "DIM", "DWS", "ADS").contains(request.layerCode())) {
      return Evaluation.reject(Verdict.UNKNOWN, "未批准的模型分层");
    }
    Set<String> identities = new HashSet<>();
    for (ColumnRequest column : request.columns()) {
      if (column == null || !text(column.columnName()) || !text(column.physicalType())
          || !identities.add(normalized(column.columnName()))) {
        return Evaluation.reject(Verdict.UNKNOWN, "请求未覆盖规范化的唯一物理列全集");
      }
      if (column.standardFieldId() == null || column.standardFieldId() <= 0) {
        return Evaluation.reject(Verdict.BLOCKED, "存在未绑定有效标准字段的非 ODS 物理列");
      }
    }
    if (observation == null || observation.projectId() != request.projectId()
        || observation.modelId() != request.modelId()
        || !Objects.equals(observation.layerCode(), request.layerCode())
        || !Objects.equals(observation.physicalStructureSha256(),
            request.physicalStructureSha256())) {
      return Evaluation.reject(Verdict.UNKNOWN, "Semantic 响应缺失或不属于当前冻结结构");
    }
    if ("BLOCKED".equals(observation.verdict())) {
      return Evaluation.reject(Verdict.BLOCKED, "Semantic 拒绝当前标准引用");
    }
    if (!"VALID".equals(observation.verdict())
        || !PROPOSED_ALGORITHM.equals(observation.digestAlgorithmId())
        || !digest(observation.semanticEvidenceSha256())
        || !text(observation.consistencyToken())
        || observation.columns() == null
        || observation.columns().size() != request.columns().size()) {
      return Evaluation.reject(Verdict.UNKNOWN, "Semantic 证据版本、观察一致性或列全集不完整");
    }

    var byName = new java.util.HashMap<String, ColumnRequest>();
    for (ColumnRequest column : request.columns()) {
      byName.put(normalized(column.columnName()), column);
    }
    Set<String> observed = new HashSet<>();
    List<ColumnFact> canonical = new ArrayList<>();
    for (ColumnFact fact : observation.columns()) {
      if (fact == null || !text(fact.columnName())) {
        return Evaluation.reject(Verdict.UNKNOWN, "Semantic 证据包含缺失列身份");
      }
      String name = normalized(fact.columnName());
      ColumnRequest expected = byName.get(name);
      if (expected == null || !observed.add(name)
          || !Objects.equals(expected.standardFieldId(), fact.standardFieldId())
          || !Objects.equals(expected.physicalType(), fact.physicalType())
          || !digest(fact.standardEvidenceSha256())) {
        return Evaluation.reject(Verdict.UNKNOWN, "Semantic 证据列身份、物理类型或标准摘要不一致");
      }
      if (!"VALID".equals(fact.standardVerdict())) {
        return Evaluation.reject("BLOCKED".equals(fact.standardVerdict())
            ? Verdict.BLOCKED : Verdict.UNKNOWN, "存在不可发布的标准字段证据");
      }
      if (!validCodeSetFact(fact)) {
        return Evaluation.reject(Verdict.UNKNOWN, "CODE 成员状态、计数或摘要自相矛盾");
      }
      canonical.add(fact);
    }
    canonical.sort(Comparator.comparing(c -> normalized(c.columnName())));
    String frozenDigest = candidateDigest(request, observation, canonical);
    FrozenCandidate candidate = new FrozenCandidate(request.projectId(), request.modelId(),
        request.layerCode(),
        request.physicalStructureSha256(), observation.digestAlgorithmId(),
        observation.semanticEvidenceSha256(), observation.consistencyToken(),
        frozenDigest, List.copyOf(canonical));
    return new Evaluation(Verdict.VALID_CANDIDATE,
        "仅是隔离分支验证候选；没有发布授权或跨事务一致性保证", candidate);
  }

  private static boolean validCodeSetFact(ColumnFact fact) {
    if ("NOT_APPLICABLE".equals(fact.codeSetStatus())) {
      return fact.codeSetItemCount() == null && fact.codeSetEnabledCount() == null
          && fact.codeSetSha256() == null;
    }
    if (!"ENABLED".equals(fact.codeSetStatus())) {
      return false;
    }
    return fact.codeSetItemCount() != null && fact.codeSetItemCount() > 0
        && Objects.equals(fact.codeSetItemCount(), fact.codeSetEnabledCount())
        && digest(fact.codeSetSha256());
  }

  private static String candidateDigest(Request request, Observation observation,
                                        List<ColumnFact> columns) {
    StringBuilder stable = new StringBuilder();
    append(stable, Long.toString(request.projectId()));
    append(stable, Long.toString(request.modelId()));
    append(stable, request.layerCode());
    append(stable, request.physicalStructureSha256());
    append(stable, observation.digestAlgorithmId());
    append(stable, observation.semanticEvidenceSha256());
    append(stable, observation.consistencyToken());
    for (ColumnFact fact : columns) {
      append(stable, normalized(fact.columnName()));
      append(stable, fact.physicalType());
      append(stable, Long.toString(fact.standardFieldId()));
      append(stable, fact.standardEvidenceSha256());
      append(stable, fact.codeSetStatus());
      append(stable, fact.codeSetSha256());
      append(stable, Objects.toString(fact.codeSetItemCount(), null));
      append(stable, Objects.toString(fact.codeSetEnabledCount(), null));
    }
    try {
      byte[] sha = MessageDigest.getInstance("SHA-256")
          .digest(stable.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(sha);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 不可用", ex);
    }
  }

  private static void append(StringBuilder out, String value) {
    if (value == null) out.append("-1:");
    else out.append(value.length()).append(':').append(value);
  }

  private static String normalized(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean digest(String value) {
    return value != null && value.matches("[a-f0-9]{64}");
  }

  private static boolean text(String value) {
    return value != null && !value.isBlank();
  }
}
