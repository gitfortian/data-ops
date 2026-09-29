package io.yak.ops.spi.section;

import java.time.Instant;

/** Reference to verifiable source evidence, never generated narrative. */
public record SectionEvidence(String sourceDomain, String referenceId, Instant observedAt) {
}
