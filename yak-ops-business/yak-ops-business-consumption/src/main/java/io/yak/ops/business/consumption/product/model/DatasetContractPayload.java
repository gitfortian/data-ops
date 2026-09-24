package io.yak.ops.business.consumption.product.model;

import java.util.List;

/** Dataset-specific stable contract projected from the current immutable Dataset version. */
public record DatasetContractPayload(
    long versionId,
    int versionNo,
    List<DatasetColumnContract> columns,
    List<String> capabilities) implements ProductContractPayload {

  public DatasetContractPayload {
    columns = columns == null ? List.of() : List.copyOf(columns);
    capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
  }

  @Override
  public ProductType productType() {
    return ProductType.DATASET;
  }

  public record DatasetColumnContract(
      String fieldId,
      String physicalName,
      String displayName,
      String dataType,
      boolean nullable,
      String description,
      String defaultRole,
      int sortOrder) {}
}
