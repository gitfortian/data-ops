package io.yak.ops.business.asset.application.reader.model;

import lombok.Data;

@Data
public class DatasetSummary {

    private String datasetName;

    private String schema;

    private Integer columnCount;

    private String owner;
}
