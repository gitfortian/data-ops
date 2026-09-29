package io.yak.ops.spi.section;

import java.time.Instant;
import java.util.List;

/**
 * Read-only presentation of a source domain fact in Asset Detail.
 * The owning domain retains the truth; a section never persists a second copy.
 */
public interface SectionContract {
    SectionType sectionType();

    SectionStatus status();

    String ownerDomain();

    SectionSummary summary();

    /** Required for every non-OK status; must not disclose protected facts. */
    String reason();

    /** Time of the source fact, not the time this response was assembled. */
    Instant updatedAt();

    List<SectionAction> actions();

    List<SectionEvidence> evidence();

    SectionProvenance provenance();

    SectionCapability capability();
}
