package io.yak.ops.business.development.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.util.List;

/** Authoring definition owned by a DATA_SERVICE node in Data Development. */
public record DevelopmentDataServiceDefinition(
    @JsonSerialize(using = ToStringSerializer.class) long sourceTaskAssetId,
    @JsonSerialize(using = ToStringSerializer.class) long sourceTaskRevisionId,
    int sourceTaskRevisionNo,
    String serviceName,
    String path,
    String method,
    List<ParameterContract> parameters,
    List<ResponseFieldContract> responseFields,
    int maxRows,
    int timeoutSeconds,
    String description,
    @JsonSerialize(using = ToStringSerializer.class) long dataSourceId,
    String sql,
    boolean paginationEnabled,
    Boolean autoParseParameters) {

  public DevelopmentDataServiceDefinition {
    parameters = parameters == null ? List.of() : List.copyOf(parameters);
    responseFields = responseFields == null ? List.of() : List.copyOf(responseFields);
  }

  /**
   * Backward-compatible constructor for standalone revisions created before query options were added.
   */
  public DevelopmentDataServiceDefinition(
      long sourceTaskAssetId,
      long sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      String serviceName,
      String path,
      String method,
      List<ParameterContract> parameters,
      List<ResponseFieldContract> responseFields,
      int maxRows,
      int timeoutSeconds,
      String description,
      long dataSourceId,
      String sql) {
    this(
        sourceTaskAssetId,
        sourceTaskRevisionId,
        sourceTaskRevisionNo,
        serviceName,
        path,
        method,
        parameters,
        responseFields,
        maxRows,
        timeoutSeconds,
        description,
        dataSourceId,
        sql,
        false,
        Boolean.TRUE);
  }

  /**
   * Backward-compatible constructor for historical revisions that pinned a SQL TaskRevision.
   *
   * <p>New Data Service revisions are standalone and store their own dataSourceId + SQL. The legacy
   * fields remain readable so already-published APIs do not break during the lightweight v1
   * transition.
   */
  public DevelopmentDataServiceDefinition(
      long sourceTaskAssetId,
      long sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      String serviceName,
      String path,
      String method,
      List<ParameterContract> parameters,
      List<ResponseFieldContract> responseFields,
      int maxRows,
      int timeoutSeconds,
      String description) {
    this(
        sourceTaskAssetId,
        sourceTaskRevisionId,
        sourceTaskRevisionNo,
        serviceName,
        path,
        method,
        parameters,
        responseFields,
        maxRows,
        timeoutSeconds,
        description,
        0L,
        null,
        false,
        Boolean.TRUE);
  }

  public boolean standaloneSql() {
    return dataSourceId > 0L && sql != null && !sql.isBlank();
  }

  /** Historical JSON has no autoParseParameters field; keep the previous auto-discovery behavior. */
  public boolean autoParseParametersEnabled() {
    return autoParseParameters == null || autoParseParameters;
  }

  /** A published v1 Data Service Revision must be directly deployable by Runtime. */
  public void validatePublishable() {
    if (!"GET".equalsIgnoreCase(method)) {
      throw new IllegalArgumentException("当前 Data Service Runtime 仅支持 GET");
    }
    if (dataSourceId <= 0L) {
      throw new IllegalArgumentException("发布前请选择数据源");
    }
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("发布前请填写查询 SQL");
    }
    if (responseFields.isEmpty()) {
      throw new IllegalArgumentException("发布前请先运行查询并确认响应字段");
    }
    for (ParameterContract parameter : parameters) {
      if (parameter == null) {
        throw new IllegalArgumentException("请求参数 Contract 不能为空");
      }
      if (!parameter.required()) {
        throw new IllegalArgumentException(
            "第一版 Runtime 仅支持必填请求参数，请将参数设为必填：" + parameter.name());
      }
      if ("OBJECT".equalsIgnoreCase(parameter.type())) {
        throw new IllegalArgumentException(
            "第一版 Runtime 请求参数暂不支持 OBJECT：" + parameter.name());
      }
    }
  }

  public record ParameterContract(
      String name,
      String type,
      boolean required,
      String description,
      String example) {}

  public record ResponseFieldContract(
      String name,
      String type,
      boolean nullable,
      String description,
      String example) {}
}
