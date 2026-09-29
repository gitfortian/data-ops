package io.yak.ops.business.modeling.domain;

/** Business attribute in logical modeling layer. */
public record LogicalAttribute(
    Long id,
    Long entityId,
    String code,
    String name,
    String logicalType,
    String description,
    Boolean primaryFlag,
    Boolean nullable,
    Integer sort) {

  public static LogicalAttribute create(
      Long entityId, String code, String name, String logicalType) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Attribute code must not be blank");
    }
    return new LogicalAttribute(entityId == null ? null : null, entityId, code, name,
        logicalType, null, false, true, 0);
  }
}
