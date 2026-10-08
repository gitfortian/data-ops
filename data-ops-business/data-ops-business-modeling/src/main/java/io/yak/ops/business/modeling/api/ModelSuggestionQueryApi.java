package io.yak.ops.business.modeling.api;

/** Authorized source definition for workbench suggestions, not a model command. */
public interface ModelSuggestionQueryApi {
  Context require(long modelId);
  record Context(long modelId, String name, String dialect, String definition) {}
}
