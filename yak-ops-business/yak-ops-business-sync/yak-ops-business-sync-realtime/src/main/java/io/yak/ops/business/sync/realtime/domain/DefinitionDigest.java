package io.yak.ops.business.sync.realtime.domain;

/** Semantic SHA-256 digest of a canonical SyncDefinition plus RuntimeEnvironmentRef. */
public record DefinitionDigest(String value) {
  public DefinitionDigest {
    if (value == null || !value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("DefinitionDigest 必须是 64 位小写 SHA-256");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
