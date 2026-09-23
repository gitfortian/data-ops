package io.yak.ops.spi.section;

/**
 * Contract describing an Asset Governance Hub section.
 *
 * A section is a governance context expression and does not own business facts.
 */
public interface SectionContract {

    String id();

    String title();

    SectionStatus status();
}
