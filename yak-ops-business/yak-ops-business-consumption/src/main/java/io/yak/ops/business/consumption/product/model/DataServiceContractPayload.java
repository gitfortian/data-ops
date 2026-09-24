package io.yak.ops.business.consumption.product.model;

import java.util.List;

/** Data Service-specific published interface/runtime contract. */
public record DataServiceContractPayload(
    String runtimePath,
    List<String> parameterNames,
    String authMode,
    int maxRows,
    int timeoutSeconds,
    boolean paginationEnabled,
    boolean enabled,
    String sourceType,
    String sourceRef,
    Long sourceRevisionId,
    Integer sourceRevisionNo,
    long runtimeGeneration) implements ProductContractPayload {

  public DataServiceContractPayload {
    parameterNames = parameterNames == null ? List.of() : List.copyOf(parameterNames);
  }

  @Override
  public ProductType productType() {
    return ProductType.DATA_SERVICE;
  }
}
