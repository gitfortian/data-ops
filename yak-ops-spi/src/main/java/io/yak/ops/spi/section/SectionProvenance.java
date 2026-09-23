package io.yak.ops.spi.section;

import java.time.Instant;

/** Where and when the summary was read; independent of Asset's own ledger. */
public record SectionProvenance(String sourceDomain, String sourceId, Instant observedAt) {
}
