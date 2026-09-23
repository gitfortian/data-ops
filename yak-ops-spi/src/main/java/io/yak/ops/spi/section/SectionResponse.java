package io.yak.ops.spi.section;

import java.util.List;

/**
 * Aggregate response returned by section query boundary.
 */
public record SectionResponse(
        String assetId,
        List<SectionResult> sections) {
}
