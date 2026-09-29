package io.yak.ops.business.dataservice.domain;

import java.time.LocalDateTime;

/** Minimal source-owned event for a successful invocation; it carries no request or response data. */
public record DataServiceSuccessfulInvocationEvent(
    Long id,
    Long projectId,
    Long apiId,
    Long consumerId,
    String consumerName,
    Long sourceRevisionId,
    Integer sourceRevisionNo,
    LocalDateTime observedAt) {}
