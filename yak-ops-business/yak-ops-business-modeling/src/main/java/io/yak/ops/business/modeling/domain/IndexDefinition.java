package io.yak.ops.business.modeling.domain;

import java.util.List;

/**
 * A secondary index of a model's table. Names are unique within the model
 * (case-insensitive; PRIMARY is reserved); referenced columns must exist in
 * the saved column list. Full-replace semantics like the columns themselves.
 */
public record IndexDefinition(
    Long id,
    String indexName,
    Boolean uniqueIndex,
    String indexType,
    List<String> columns) {}
