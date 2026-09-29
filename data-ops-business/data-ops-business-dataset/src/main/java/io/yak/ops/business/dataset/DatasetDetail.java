package io.yak.ops.business.dataset;

import java.util.List;

public record DatasetDetail(
    Dataset dataset,
    DatasetVersion currentVersion,
    List<DatasetVersion> versions,
    List<DatasetField> fields) {
}
