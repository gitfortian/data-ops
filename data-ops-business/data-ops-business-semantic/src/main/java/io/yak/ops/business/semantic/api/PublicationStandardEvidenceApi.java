package io.yak.ops.business.semantic.api;

import java.util.List;

/**
 * R14-B2 T1: internal, read-only Semantic-owned publication evidence SPI.
 *
 * <p>This is the approved R1 DEVELOPMENT contract skeleton, NOT a publication
 * permit. No implementation is registered until trusted project/subject scope,
 * full-field applicability, monotonic Semantic revision and publish fencing
 * have passed their integration gates. Modeling must not read Semantic tables.
 * Never accept a client-supplied project ID as authorization.
 */
public interface PublicationStandardEvidenceApi {

  enum Verdict { VALID, BLOCKED, UNKNOWN }

  /** Physical facts supplied by Modeling from its locked, complete StructureView. */
  record ColumnRequest(
      String columnName, String physicalType, Long standardFieldId, String physicalRole) {}

  /**
   * Project/model/structure are part of evidence identity, NOT an auth context.
   * The implementation must independently resolve trusted project and access.
   */
  record BatchRequest(long projectId, long modelId, String layerCode,
                      String physicalStructureSha256, List<ColumnRequest> columns) {
    public BatchRequest {
      columns = List.copyOf(columns);
    }
  }

  /** No raw SECURITY masking rule, credential, secret or other project data. */
  record StandardReference(
      String kind, Long standardId, Integer version, String status,
      String definitionSha256) {}

  /** All CODE members, not only the enabled subset, contribute to its digest. */
  record CodeSetReference(
      String codeSetCode, String status, int memberCount, int enabledMemberCount,
      String digestAlgorithmId, String contentSha256) {}

  /** Exact per-column identity plus the safely disclosable applicable standards. */
  record ColumnEvidence(
      String columnName, String physicalType, Long standardFieldId,
      String semanticRole, String status, StandardReference standardField,
      List<StandardReference> applicableStandards, CodeSetReference codeSet,
      String safeReasonCode) {
    public ColumnEvidence {
      applicableStandards = List.copyOf(applicableStandards);
    }
  }

  /**
   * Semantic's candidate current-state observation, not an immutable approved
   * historical version and not authorization to create ModelVersion.
   *
   * <p>semanticRevision must be a persisted project-scoped monotonically
   * increasing revision, not a content digest, time stamp or random token.
   * Until that fact and read consistency are implemented, providers MUST
   * respond UNKNOWN, never return VALID with a fabricated revision.
   */
  record BatchResult(
      Verdict verdict, long projectId, long modelId, String layerCode,
      String physicalStructureSha256, Long semanticRevision,
      String digestAlgorithmId, String evidenceSha256,
      List<ColumnEvidence> columns, List<String> safeReasonCodes) {
    public BatchResult {
      columns = List.copyOf(columns);
      safeReasonCodes = List.copyOf(safeReasonCodes);
      // Never let an incomplete or provider-unknown result impersonate VALID.
      // This is a DTO shape check, not authorization, revision provenance or a
      // release gate. The future provider must still prove all three.
      if (verdict == null) {
        throw new IllegalArgumentException("标准证据结果缺少有效判定");
      }
      if (verdict == Verdict.VALID) {
        if (projectId <= 0 || modelId <= 0 || layerCode == null
            || physicalStructureSha256 == null
            || !physicalStructureSha256.matches("[a-f0-9]{64}")
            || semanticRevision == null || semanticRevision <= 0
            || digestAlgorithmId == null || digestAlgorithmId.isBlank()
            || evidenceSha256 == null || !evidenceSha256.matches("[a-f0-9]{64}")
            || columns.isEmpty() || !safeReasonCodes.isEmpty()
            || columns.stream().anyMatch(column -> column == null
                || column.standardFieldId() == null || column.standardFieldId() <= 0
                || !"VALID".equals(column.status()))) {
          throw new IllegalArgumentException("VALID 缺少完整可信修订或逐字段标准证据");
        }
      }
    }
  }

  /**
   * Resolve current server-side security context; read and validate the whole
   * scoped batch, with safe failures. No standard, model or target writes.
   */
  BatchResult evaluate(BatchRequest request);
}
