package io.yak.ops.spi.modeling;

/**
 * Modeling-owned immutable DWD evidence read SPI for DataDev.
 * Never grants permission to bind or run a task; the caller must recheck
 * project and operational target permissions within its own transaction.
 */
public interface PublishedDwdDesignEvidenceProvider {
  Evidence inspect(long modelId, int versionNo);

  record Evidence(
      long projectId,
      long modelId,
      long modelVersionId,
      int modelVersionNo,
      String targetTableCode,
      long mappingRevisionId,
      String mappingContentSha256,
      String logicalSnapshotSha256,
      String physicalStructureSha256,
      String currentStandardEvidenceSha256,
      boolean reviewable,
      String status) {}
}
