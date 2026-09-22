package io.yak.ops.business.dataservice.publication.source;

import java.time.Instant;
import java.util.List;

/** Supplies immutable, publishable upstream revisions to the Data Service publication boundary. */
public interface DataServiceSourceProvider {

  String sourceType();

  default boolean managesServiceDefinition() {
    return false;
  }

  SourcePage list(int pageNo, int pageSize, String keyword);

  ResolvedSource resolve(String sourceRef);

  record SourcePage(List<SourceDescriptor> records, long total, int pageNo, int pageSize) {
    public SourcePage {
      records = records == null ? List.of() : List.copyOf(records);
    }
  }

  record SourceDescriptor(
      String sourceType,
      String sourceRef,
      String name,
      String sourceKind,
      String status,
      Long sourceRevisionId,
      Integer sourceRevisionNo,
      Long dataSourceId,
      Integer maxRows,
      Integer timeoutSeconds,
      String defaultPath,
      String description,
      Instant updateTime,
      Boolean paginationEnabled) {

    public SourceDescriptor(
        String sourceType, String sourceRef, String name, String sourceKind, String status,
        Long sourceRevisionId, Integer sourceRevisionNo, Long dataSourceId, Integer maxRows,
        Integer timeoutSeconds, String defaultPath, String description, Instant updateTime) {
      this(sourceType, sourceRef, name, sourceKind, status, sourceRevisionId, sourceRevisionNo,
          dataSourceId, maxRows, timeoutSeconds, defaultPath, description, updateTime, Boolean.FALSE);
    }

    public SourceDescriptor(
        String sourceType, String sourceRef, String name, String sourceKind, String status,
        Long sourceRevisionId, Integer sourceRevisionNo, Long dataSourceId, Integer timeoutSeconds,
        String defaultPath, Instant updateTime) {
      this(sourceType, sourceRef, name, sourceKind, status, sourceRevisionId, sourceRevisionNo,
          dataSourceId, null, timeoutSeconds, defaultPath, null, updateTime, Boolean.FALSE);
    }
  }

  record ParameterContract(
      String name, String type, boolean required, String description, String example) {}

  record ResponseFieldContract(
      String name, String type, boolean nullable, String description, String example) {}

  record SourceContract(
      List<ParameterContract> parameters,
      List<ResponseFieldContract> responseFields) {
    public SourceContract {
      parameters = parameters == null ? List.of() : List.copyOf(parameters);
      responseFields = responseFields == null ? List.of() : List.copyOf(responseFields);
    }

    public static SourceContract empty() {
      return new SourceContract(List.of(), List.of());
    }
  }

  record ResolvedSource(SourceDescriptor descriptor, String sql, SourceContract contract) {
    public ResolvedSource(SourceDescriptor descriptor, String sql) {
      this(descriptor, sql, SourceContract.empty());
    }
  }
}
