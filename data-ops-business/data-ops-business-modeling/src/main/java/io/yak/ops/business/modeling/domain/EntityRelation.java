package io.yak.ops.business.modeling.domain;

/** Relationship between logical entities. */
public record EntityRelation(
    Long id,
    Long sourceEntityId,
    Long targetEntityId,
    String relationType,
    String cardinality,
    String description) {}
