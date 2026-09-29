package io.yak.ops.business.modeling.domain;

import java.time.LocalDateTime;

/**
 * Lightweight version metadata for the versions panel (no structure_json).
 */
public record ModelVersionSummary(
    Long id,
    Long modelId,
    int versionNo,
    int columnCount,
    String checksum,
    String publishedBy,
    LocalDateTime publishTime) {
}
