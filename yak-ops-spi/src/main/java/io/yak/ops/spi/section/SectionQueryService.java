package io.yak.ops.spi.section;

/**
 * Application boundary for Asset Detail section retrieval.
 */
public interface SectionQueryService {

    SectionResponse getSections(String assetId);
}
