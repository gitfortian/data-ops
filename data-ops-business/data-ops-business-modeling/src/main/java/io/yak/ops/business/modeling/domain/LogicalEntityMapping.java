package io.yak.ops.business.modeling.domain;

public record LogicalEntityMapping(
    Long id,
    Long logicalEntityId,
    Long physicalTableId,
    String mappingType,
    MappingStatus status,
    String description) {

  public static LogicalEntityMapping create(
      Long logicalEntityId, Long physicalTableId, String mappingType) {
    if (logicalEntityId == null) {
      throw new IllegalArgumentException("Logical entity id must not be null");
    }
    if (physicalTableId == null) {
      throw new IllegalArgumentException("Physical table id must not be null");
    }
    return new LogicalEntityMapping(
        null, logicalEntityId, physicalTableId, mappingType, MappingStatus.DRAFT, null);
  }
}
