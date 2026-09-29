package io.yak.ops.business.dataservice.publication;

public record PublicationSettings(
    String name,
    String path,
    Integer maxRows,
    Integer timeoutSeconds,
    Boolean enabled,
    String description) {}
