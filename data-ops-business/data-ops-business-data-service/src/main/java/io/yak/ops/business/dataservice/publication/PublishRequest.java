package io.yak.ops.business.dataservice.publication;

public record PublishRequest(
    String sourceType,
    String sourceRef,
    String name,
    String path,
    Integer maxRows,
    Integer timeoutSeconds,
    Boolean enabled,
    String description) {}
