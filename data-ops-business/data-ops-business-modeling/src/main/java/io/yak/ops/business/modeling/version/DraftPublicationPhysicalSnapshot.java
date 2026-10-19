package io.yak.ops.business.modeling.version;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.ColumnRequest;
import io.yak.ops.business.modeling.standard.DraftPublicationEvidenceVerifier.Request;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.common.version.VersionDigests;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Review-only bridge from the real Modeling physical-structure DTO to the
 * proposed non-ODS standard-evidence request.
 *
 * <p>Uses exactly the existing ModelVersionService semantic projection (which
 * drops storage row IDs and volatile draft status) for its physical fingerprint.
 * Does not fetch a model, acquire a root lock, resolve a trusted project, invoke
 * Semantic or grant publication. A caller must provide a server-owned snapshot
 * under its own authorization/transaction contract. No Spring bean.
 */
public final class DraftPublicationPhysicalSnapshot {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> NON_ODS = Set.of("DWD", "DIM", "DWS", "ADS");

  private DraftPublicationPhysicalSnapshot() {}

  /**
   * Candidate only. The project ID and layer MUST eventually originate from
   * the trusted Modeling root and server-side authorization context.
   */
  public static Request from(
      long projectId, long modelId, String layerCode, StructureView structure) {
    if (projectId <= 0 || modelId <= 0 || !NON_ODS.contains(
        layerCode == null ? "" : layerCode)
        || structure == null || !Objects.equals(structure.modelId(), modelId)
        || structure.columns() == null || structure.columns().isEmpty()) {
      throw new IllegalArgumentException(
          "Modeling 物理结构快照或身份不完整，不能建立候选证据请求");
    }
    List<ColumnRequest> columns = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (StructureView.ColumnView column : structure.columns()) {
      if (column == null || !hasText(column.columnName())
          || !hasText(column.dataType())
          || !names.add(column.columnName().trim().toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException("物理结构含重复或缺失的列身份");
      }
      // Keep missing/non-positive StandardField IDs: the verifier must return
      // BLOCKED rather than silently excluding the column from the full set.
      columns.add(new ColumnRequest(
          column.columnName(), column.dataType(), column.stdFieldId()));
    }
    String fingerprint = fingerprint(structure);
    return new Request(projectId, modelId, layerCode, fingerprint, columns);
  }

  /**
   * Same DTO projection and JSON defaults as ModelVersionService.fingerprint;
   * no legacy row-ID, model status or saved-column ID is part of the digest.
   */
  static String fingerprint(StructureView structure) {
    try {
      return VersionDigests.sha256Hex(
          MAPPER.writeValueAsString(ModelVersionService.semanticView(structure)));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("物理模型语义结构摘要计算失败", ex);
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
