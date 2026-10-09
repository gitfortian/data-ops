package io.yak.ops.platform.security.port;

/** Stable, nullable query fields: keeps the original DTO filtering semantics. */
public record UserProjectCriteria(Long id, Long userId, Integer userType,
                                  Long projectId, Boolean isDelete) {}
