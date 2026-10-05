package io.yak.ops.business.agent.domain;

import java.util.List;

/** 结构化数据集查询规格。由模型产出、经白名单校验后才可执行。禁止携带 SQL 文本。 */
public record DatasetQuerySpec(
    long datasetId,
    List<String> dimensions,
    List<Metric> metrics,
    List<Filter> filters,
    List<Sort> sorts,
    Integer limit,
    Integer versionNo) {

  public DatasetQuerySpec(long datasetId, List<String> dimensions, List<Metric> metrics,
      List<Filter> filters, List<Sort> sorts, Integer limit) {
    this(datasetId, dimensions, metrics, filters, sorts, limit, null);
  }

  public DatasetQuerySpec withVersion(Integer version) {
    return new DatasetQuerySpec(datasetId, dimensions, metrics, filters, sorts, limit, version);
  }

  public record Metric(String fieldId, Aggregation aggregation) {}

  public enum Aggregation {
    SUM,
    AVG,
    COUNT,
    COUNT_DISTINCT,
    MAX,
    MIN
  }

  public record Filter(String fieldId, Operator operator, String value, List<String> values) {}

  public enum Operator {
    EQ,
    NE,
    GT,
    GTE,
    LT,
    LTE,
    IN,
    NOT_IN,
    LIKE,
    NOT_LIKE,
    BETWEEN,
    IS_NULL,
    IS_NOT_NULL;

    /** 是否需要单值参数。 */
    public boolean needsSingleValue() {
      return this != IN && this != NOT_IN && this != BETWEEN && this != IS_NULL && this != IS_NOT_NULL;
    }

    /** 是否需要多值参数。 */
    public boolean needsValues() {
      return this == IN || this == NOT_IN || this == BETWEEN;
    }

    /** 是否无参。 */
    public boolean needsNoValue() {
      return this == IS_NULL || this == IS_NOT_NULL;
    }
  }

  public record Sort(String fieldId, Direction direction) {

    public enum Direction {
      ASC,
      DESC
    }
  }
}
