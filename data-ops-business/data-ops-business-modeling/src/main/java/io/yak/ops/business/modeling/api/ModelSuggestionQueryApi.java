package io.yak.ops.business.modeling.api;

/** Authorized source definition for workbench suggestions, not a model command. */
public interface ModelSuggestionQueryApi {
  Context require(long modelId);
  default Fields fields(long modelId) { throw new IllegalStateException("模型字段辅助读取未装配"); }
  record Context(long modelId, String name, String dialect, String definition) {}
  record Field(String name, String type, String description) {}
  record Fields(long modelId, String definition, java.util.List<Field> fields) {}
}
