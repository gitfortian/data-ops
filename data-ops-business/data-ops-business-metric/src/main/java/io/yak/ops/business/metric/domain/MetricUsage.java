package io.yak.ops.business.metric.domain;

import java.time.LocalDateTime;

/** A recorded downstream reference to a Metric, with a nullable legacy-unknown version. */
public record MetricUsage(
    Long id,
    Long metricId,
    Integer metricVersion,
    String usageType,
    Long usageId,
    String usageName,
    LocalDateTime createdAt) {}
