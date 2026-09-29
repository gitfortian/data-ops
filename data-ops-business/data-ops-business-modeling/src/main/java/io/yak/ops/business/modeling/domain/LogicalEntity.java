package io.yak.ops.business.modeling.domain;

import java.time.LocalDateTime;

/** Business semantic entity in logical modeling layer. */
public record LogicalEntity(
    Long id,
    Long logicalModelId,
    String code,
    String name,
    String businessName,
    String description,
    String owner,
    ModelStatus status,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public static LogicalEntity create(
      Long logicalModelId, String code, String name, String businessName, String description) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Entity code must not be blank");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Entity name must not be blank");
    }
    return new LogicalEntity(
        null,
        logicalModelId,
        code,
        name.trim(),
        businessName,
        description,
        null,
        ModelStatus.DRAFT,
        null,
        null);
  }

  public LogicalEntity withPersisted(Long id, String owner, LocalDateTime createTime, LocalDateTime updateTime) {
    return new LogicalEntity(id, logicalModelId, code, name, businessName, description,
        owner, status, createTime, updateTime);
  }
}
