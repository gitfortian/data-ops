package io.yak.ops.business.dataservice.domain;

/** Server-resolved executable snapshot pinned when an upstream source revision is published. */
public record PublishedRuntimeSnapshot(Long dataSourceId, String sql) {}
