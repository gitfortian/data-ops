package io.yak.ops.spi.semantic;

import java.util.List;

/**
 * Metadata-owned, project/actor scoped source revalidation for Semantic adoption.
 * The caller must not trust task plan text or the user's supplied datasource identity.
 */
public interface SourceSchemaAdoptionProof {
  record Expected(long projectId, long dataSourceId, String captureId,
      String evidenceFingerprint, List<String> tableAssetKeys) {
    public Expected { tableAssetKeys = List.copyOf(tableAssetKeys); }
  }
  /** Fail closed on missing permissions, cross-project selection or harvest drift. */
  void assertCurrent(Expected expected);
  /** Resolve ONLY an asset in a freshly verified authorized physical snapshot. */
  String verifiedTableName(Expected expected,String assetKey);
}
