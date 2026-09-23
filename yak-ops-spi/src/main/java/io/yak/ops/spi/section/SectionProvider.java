package io.yak.ops.spi.section;

/**
 * Extension point for section data providers.
 */
public interface SectionProvider {

    String sectionId();

    SectionContract provide();
}
