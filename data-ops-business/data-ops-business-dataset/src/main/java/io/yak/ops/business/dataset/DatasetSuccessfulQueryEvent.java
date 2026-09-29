package io.yak.ops.business.dataset;

import java.time.Instant;

/** Minimal source-owned event for a successful Dataset query; it carries no SQL or result data. */
public record DatasetSuccessfulQueryEvent(
    Long projectId,
    String queryId,
    long datasetId,
    Long datasetVersionId,
    Integer datasetVersionNo,
    String subjectType,
    String subjectSourceDomain,
    String subjectSourceIdentity,
    String subjectDisplayHint,
    Instant startedAt) {}
