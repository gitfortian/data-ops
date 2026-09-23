package io.yak.ops.spi.section;

/** Asset Detail application boundary. The Asset itself must resolve first. */
public interface SectionQueryService {
    SectionResponse getSections(SectionContext context);
}
