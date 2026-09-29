package io.yak.ops.business.modeling.domain;

public record LogicalAttributeMapping(
    Long id,
    Long logicalAttributeId,
    Long physicalColumnId,
    String mappingExpression,
    MappingStatus status) {

  public static LogicalAttributeMapping create(
      Long logicalAttributeId, Long physicalColumnId) {
    if (logicalAttributeId == null) {
      throw new IllegalArgumentException("Logical attribute id must not be null");
    }
    if (physicalColumnId == null) {
      throw new IllegalArgumentException("Physical column id must not be null");
    }
    return new LogicalAttributeMapping(
        null, logicalAttributeId, physicalColumnId, null, MappingStatus.DRAFT);
  }
}
