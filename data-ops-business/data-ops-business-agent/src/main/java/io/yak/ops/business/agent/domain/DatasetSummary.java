package io.yak.ops.business.agent.domain;

import java.util.List;

/** 数据集目录视图（面向 LLM 的只读投影）。字段语义真相归 dataset 模块。 */
public record DatasetSummary(long id, String name, String description, boolean online) {

  /** 数据集字段视图。role 取值为 DIMENSION / MEASURE。 */
  public record FieldView(
      String fieldId,
      String displayName,
      String dataType,
      String role,
      boolean nullable,
      String description) {}

  public record DatasetFields(long datasetId, String name, List<FieldView> fields) {}
}
