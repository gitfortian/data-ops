package io.yak.ops.business.modeling.domain;

/** Relationship between logical entities. */
public record EntityRelation(
    Long id,
    Long sourceEntityId,
    Long targetEntityId,
    String relationType,
    String cardinality,
    String description) {

  public static EntityRelation create(
      Long sourceEntityId,
      Long targetEntityId,
      String relationType,
      String cardinality,
      String description) {
    if (sourceEntityId == null || targetEntityId == null) {
      throw new IllegalArgumentException("Entity relation endpoints must not be null");
    }
    if (relationType == null || relationType.isBlank()) {
      throw new IllegalArgumentException("Relation type must not be blank");
    }
    return new EntityRelation(null, sourceEntityId, targetEntityId, relationType, cardinality, description);
  }
}
