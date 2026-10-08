package io.yak.ops.business.agent.domain;

import java.util.List;

public record ModelMappingContext(String modelName, String dialect, String targetColumn, String targetType,
    String targetDescription, String definition, String sourceDefinition, List<SourceColumn> columns, boolean truncated) {
  public record SourceColumn(String name, String type, String description, boolean nullable) {}
}
