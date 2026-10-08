package io.yak.ops.business.agent.domain;

/** A model identity plus explicitly unpersisted field input; never a source fact or permission. */
public record StandardMatchTarget(long modelId, String columnName, String dataType,
    String businessDescription, String keyword) {
  public StandardMatchTarget {
    if (modelId <= 0 || columnName == null || !columnName.matches("[A-Za-z0-9_][A-Za-z0-9_$]{0,127}")
        || dataType == null || dataType.isBlank() || dataType.length() > 64
        || businessDescription == null || businessDescription.length() > 512
        || keyword == null || keyword.length() > 64) throw new IllegalArgumentException("标准匹配字段草稿无效");
  }
}
