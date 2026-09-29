package io.yak.ops.business.modeling.domain;

import java.time.LocalDateTime;

/** Logical business model aggregate root. */
public record LogicalModel(
    Long id,
    String code,
    String name,
    String description,
    Long domainId,
    String owner,
    ModelStatus status,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public static LogicalModel create(String code, String name, String description, Long domainId) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Logical model code must not be blank");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Logical model name must not be blank");
    }
    return new LogicalModel(null, code, name.trim(), description, domainId, null, ModelStatus.DRAFT, null, null);
  }

  public LogicalModel withPersisted(Long id, String owner, LocalDateTime createTime, LocalDateTime updateTime) {
    return new LogicalModel(id, code, name, description, domainId, owner, status, createTime, updateTime);
  }
}
