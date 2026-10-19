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
    }
  }

  /**
   * Resolve current server-side security context; read and validate the whole
   * scoped batch, with safe failures. No standard, model or target writes.
   */
  BatchResult evaluate(BatchRequest request);
}
