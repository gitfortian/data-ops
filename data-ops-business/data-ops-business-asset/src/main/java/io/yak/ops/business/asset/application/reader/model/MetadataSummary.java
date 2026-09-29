package io.yak.ops.business.asset.application.reader.model;

import lombok.Data;

@Data
public class MetadataSummary {

    private String datasource;

    private String database;

    private String table;

    private Integer columnCount;

    private Long lastSyncTime;
}
