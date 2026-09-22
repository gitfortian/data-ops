package io.yak.ops.business.dataset.publication;

import io.yak.ops.business.dataset.schema.DatasetFieldSpec;
import java.util.List;

/** Command for publishing an immutable TaskAsset revision into a Dataset version. */
public record DatasetPublishCommand(
    long sourceTaskAssetId,
    String name,
    String description,
    List<DatasetFieldSpec> fields) {
  public DatasetPublishCommand {
    fields = fields == null ? List.of() : List.copyOf(fields);
  }
}
