package io.yak.ops.spi.section;

import java.util.List;

/**
 * Query contract for aggregating asset detail sections.
 */
public interface SectionQuery {

    List<SectionResult> query(String assetId);
}
