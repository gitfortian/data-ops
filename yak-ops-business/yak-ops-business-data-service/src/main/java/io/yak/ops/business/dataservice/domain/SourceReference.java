package io.yak.ops.business.dataservice.domain;

/** Stable source identity plus the immutable published revision currently materialized by a service. */
public record SourceReference(
    String sourceType,
    String sourceRef,
    Long sourceRevisionId,
    Integer sourceRevisionNo) {}
