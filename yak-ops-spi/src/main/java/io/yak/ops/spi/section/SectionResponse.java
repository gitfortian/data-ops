package io.yak.ops.spi.section;

import java.util.List;
import java.util.Objects;

/**
 * Asset Understanding Loop: identify the object, interpret each source domain
 * with its evidence and reason, then follow authorized source-domain actions.
 */
public record SectionResponse(SectionContext asset, List<SectionResult> sections) {
    public SectionResponse {
        Objects.requireNonNull(asset, "asset");
        sections = List.copyOf(sections);
    }
}
