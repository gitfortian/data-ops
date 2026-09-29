package io.yak.ops.business.dataservice.domain;

/** Service-facing definition fields owned either locally or by an upstream published source. */
public record DataServiceSettings(
    String name,
    String path,
    int maxRows,
    int timeoutSeconds,
    boolean enabled,
    String description,
    boolean paginationEnabled) {}
