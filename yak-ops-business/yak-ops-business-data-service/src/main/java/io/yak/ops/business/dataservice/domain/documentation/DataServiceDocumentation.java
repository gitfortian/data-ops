package io.yak.ops.business.dataservice.domain.documentation;

import java.time.LocalDateTime;
import java.util.List;

/** Persisted API documentation contract bound to the SQL fingerprint that produced it. */
public record DataServiceDocumentation(
    Long apiId,
    String sqlHash,
    List<ParameterDoc> parameters,
    List<ResponseFieldDoc> responseFields,
    LocalDateTime updateTime) {

  public DataServiceDocumentation {
    parameters = parameters == null ? List.of() : List.copyOf(parameters);
    responseFields = responseFields == null ? List.of() : List.copyOf(responseFields);
  }

  public record ParameterDoc(
      String name, String type, boolean required, String description, String example) {}

  public record ResponseFieldDoc(
      String name, String type, boolean nullable, String description, String example) {}
}
