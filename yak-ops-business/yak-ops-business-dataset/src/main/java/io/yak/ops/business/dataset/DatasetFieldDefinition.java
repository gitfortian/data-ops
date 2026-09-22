package io.yak.ops.business.dataset;

/** Field contract persisted inside one immutable Dataset version. */
public record DatasetFieldDefinition(
    String fieldId,
    String physicalName,
    String displayName,
    DatasetFieldDataType dataType,
    boolean nullable,
    String description,
    DatasetFieldRole defaultRole) {
}
