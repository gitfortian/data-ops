package io.yak.ops.business.development.execution.model;

import java.util.List;

/** Paged execution-history projection; it is not the runtime state owner. */
public record DevelopmentTaskExecutionPage(
    List<DevelopmentTaskExecutionSummary> records,
    long total,
    int pageNo,
    int pageSize) {}
