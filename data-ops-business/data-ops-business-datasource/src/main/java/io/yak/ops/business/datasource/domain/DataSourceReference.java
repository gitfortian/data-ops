package io.yak.ops.business.datasource.domain;

import io.yak.ops.common.enums.datasource.DataSourceDbType;

/** Project-owned identity for definition and display reads; carries no connection credentials. */
public record DataSourceReference(Long id, Long projectId, String name, DataSourceDbType dbType) {}
