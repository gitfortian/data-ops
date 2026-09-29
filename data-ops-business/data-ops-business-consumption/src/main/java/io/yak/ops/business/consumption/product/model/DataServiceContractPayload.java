package io.yak.ops.business.consumption.product.model;

import java.util.List;
import java.time.LocalDateTime;

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
    long runtimeGeneration,
    List<ParameterContract> parameters,
    List<ResponseFieldContract> responseFields,
    boolean documented,
    boolean schemaStale,
    LocalDateTime documentationUpdatedAt) implements ProductContractPayload {

  public DataServiceContractPayload {
    parameterNames = parameterNames == null ? List.of() : List.copyOf(parameterNames);
    parameters = parameters == null ? List.of() : List.copyOf(parameters);
    responseFields = responseFields == null ? List.of() : List.copyOf(responseFields);
  }

  public record ParameterContract(
      String name, String type, boolean required, String description, String example) {}

  public record ResponseFieldContract(
      String name, String type, boolean nullable, String description, String example) {}

  @Override
  public ProductType productType() {
    return ProductType.DATA_SERVICE;
  }
}
