package io.yak.ops.spi.section;

/**
 * A section provider reads its owning domain. The caller enforces Project Space
 * and permissions; provider failures are isolated to this section.
 */
public interface SectionProvider {
    /** Asset source types for which this provider has an implementation. */
    default java.util.Set<String> supportedSourceTypes() {
        return java.util.Set.of();
    }

    SectionType sectionType();

    boolean supports(SectionContext context);

    SectionContract query(SectionContext context);
}
