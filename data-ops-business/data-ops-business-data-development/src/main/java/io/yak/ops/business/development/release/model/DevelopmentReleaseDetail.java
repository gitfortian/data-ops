package io.yak.ops.business.development.release.model;

import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.domain.DevelopmentTaskRevisionSummary;
import java.util.List;

/** Release-center detail projection composed from catalog state and immutable revisions. */
public record DevelopmentReleaseDetail(
    DevelopmentReleaseSummary release,
    DevelopmentTaskRevision currentRevision,
    List<DevelopmentTaskRevisionSummary> revisions) {}
