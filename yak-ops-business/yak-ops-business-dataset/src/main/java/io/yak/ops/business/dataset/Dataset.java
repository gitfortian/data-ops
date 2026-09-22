package io.yak.ops.business.dataset;

import java.time.Instant;

/** Stable BI-consumption asset. Dataset versions freeze their own executable source contract. */
public record Dataset(
    long id,
    Long projectId,
    String name,
    String description,
    DatasetStatus status,
    Long currentVersionId,
    Instant createTime,
    Instant updateTime) {

  /** Compatibility constructor for callers that do not inspect Dataset ownership directly. */
  public Dataset(
      long id,
      String name,
      String description,
      DatasetStatus status,
      Long currentVersionId,
      Instant createTime,
      Instant updateTime) {
    this(id, null, name, description, status, currentVersionId, createTime, updateTime);
  }

  /** Dataset is a Project Root; a persisted business Dataset must always have an owner. */
  public long requireProjectId() {
    if (projectId == null || projectId <= 0L) {
      throw new IllegalStateException("Dataset 缺少有效 Project 归属：" + id);
    }
    return projectId;
  }
}
