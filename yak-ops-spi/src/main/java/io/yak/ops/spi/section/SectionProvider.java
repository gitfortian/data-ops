package io.yak.ops.spi.section;

/**
 * A section provider reads its owning domain. The caller enforces Project Space
 * and permissions; provider failures are isolated to this section.
 */
public interface SectionProvider {
    SectionType sectionType();

    boolean supports(SectionContext context);

    SectionContract query(SectionContext context);
}
