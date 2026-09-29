package io.yak.ops.business.dataset;

public record DatasetField(
    String fieldId,
    long versionId,
    String physicalName,
    String displayName,
    DatasetFieldDataType dataType,
    boolean nullable,
    String description,
    DatasetFieldRole defaultRole,
    int sortOrder) {
}
