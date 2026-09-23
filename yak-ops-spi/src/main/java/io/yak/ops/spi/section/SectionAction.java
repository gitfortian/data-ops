package io.yak.ops.spi.section;

/** A permitted next step into the owning domain, using its stable identity. */
public record SectionAction(String label, String target, String sourceId) {
}
