package io.yak.ops.spi.section;

import java.util.List;

/** Read-side aggregation after the Asset boundary resolves identity and access. */
public interface SectionQuery {
    List<SectionResult> query(SectionContext context);
}
