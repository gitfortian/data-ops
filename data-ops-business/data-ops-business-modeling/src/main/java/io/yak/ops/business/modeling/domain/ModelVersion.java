package io.yak.ops.business.modeling.domain;

import java.time.LocalDateTime;

/**
 * Immutable version snapshot of a model's structure.
 *
 * <p>Created on publish or rollback; never mutated after insertion.
 * {@code structureJson} holds the full serialised structure so that
 * rollback can restore it without re-reading live tables.
 */
public record ModelVersion(
    Long id,
    Long modelId,
    int versionNo,
    String structureJson,
    String metaJson,
    int columnCount,
    String checksum,
    String publishedBy,
    LocalDateTime publishTime) {
}
