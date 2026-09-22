package io.yak.ops.business.dataset.schema;

import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;

/** Writable Dataset field contract before it is frozen into a DatasetVersion. */
public record DatasetFieldSpec(
    String fieldId,
    String physicalName,
    String displayName,
    DatasetFieldDataType dataType,
    boolean nullable,
    String description,
    DatasetFieldRole defaultRole) {}
