package io.yak.ops.spi.section;

import java.util.Map;

/** Marker for an owning domain's read-only, typed section summary. */
public interface SectionSummary {
    /** Stable JSON projection of the typed summary for the Asset renderer. */
    Map<String, Object> values();
}
