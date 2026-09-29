package io.yak.ops.business.modeling.domain;

public record LogicalModelVersion(
    Long id,
    Long modelId,
    Integer versionNo,
    ModelVersionStatus status,
    String snapshot,
    String createdBy) {

  public static LogicalModelVersion create(Long modelId, Integer versionNo, String snapshot, String createdBy) {
    if (modelId == null) {
      throw new IllegalArgumentException("Model id must not be null");
    }
    if (versionNo == null || versionNo <= 0) {
      throw new IllegalArgumentException("Version must be positive");
    }
    return new LogicalModelVersion(null, modelId, versionNo, ModelVersionStatus.DRAFT, snapshot, createdBy);
  }
}
