package io.yak.ops.spi.section;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Explained, source-backed section result; never a second business truth. */
public record SectionResult(
        SectionType sectionType,
        SectionStatus status,
        String ownerDomain,
        SectionSummary summary,
        String reason,
        Instant updatedAt,
        List<SectionAction> actions,
        List<SectionEvidence> evidence,
        SectionProvenance provenance,
        SectionCapability capability) implements SectionContract {

    public SectionResult {
        Objects.requireNonNull(sectionType, "sectionType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(ownerDomain, "ownerDomain");
        Objects.requireNonNull(capability, "capability");
        actions = List.copyOf(actions);
        evidence = List.copyOf(evidence);
        if (status == SectionStatus.OK && summary == null) {
            throw new IllegalArgumentException("OK requires a source summary");
        }
        if (status != SectionStatus.OK && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Non-OK requires an explanation");
        }
        if (status == SectionStatus.PERMISSION_DENIED
                && (summary != null || !evidence.isEmpty() || !actions.isEmpty())) {
            throw new IllegalArgumentException("Denied sections must not disclose protected facts");
        }
    }
}
