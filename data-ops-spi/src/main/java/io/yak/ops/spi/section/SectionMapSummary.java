package io.yak.ops.spi.section;

import java.util.Map;

/** JSON-ready values for domains whose section is an aggregate of owned facts. */
public record SectionMapSummary(Map<String, Object> values) implements SectionSummary {
}
