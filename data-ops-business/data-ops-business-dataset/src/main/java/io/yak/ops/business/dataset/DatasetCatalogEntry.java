package io.yak.ops.business.dataset;

import java.util.List;

/** Lightweight consumer catalog projection for one Dataset and its current schema. */
public record DatasetCatalogEntry(
    Dataset dataset, DatasetVersion currentVersion, List<DatasetField> fields) {

  public DatasetCatalogEntry {
    fields = fields == null ? List.of() : List.copyOf(fields);
  }
}
