package io.yak.ops.business.resource.domain;

import java.util.Locale;

/** Immutable logical resource path used by namespace operations. */
public record ResourcePath(String value) {

  public ResourcePath {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("resource path must not be blank");
    }
    String normalized = value.trim();
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }
    while (normalized.length() > 1 && normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    value = normalized;
  }

  public static ResourcePath root() {
    return new ResourcePath("/");
  }

  public ResourcePath child(String normalizedName) {
    if (normalizedName == null || normalizedName.isBlank()) {
      throw new IllegalArgumentException("resource child name must not be blank");
    }
    return new ResourcePath("/".equals(value) ? "/" + normalizedName : value + "/" + normalizedName);
  }

  public String storagePath() {
    if ("/".equals(value)) {
      throw new IllegalStateException("root resource has no storage path");
    }
    return value.substring(1);
  }

  public boolean isDescendantOf(ResourcePath ancestor) {
    if (ancestor == null || "/".equals(ancestor.value)) {
      return !"/".equals(value);
    }
    return value.startsWith(ancestor.value + "/");
  }

  public static String suffix(String name) {
    int index = name == null ? -1 : name.lastIndexOf('.');
    return index <= 0 || index == name.length() - 1
        ? null
        : name.substring(index + 1).toLowerCase(Locale.ROOT);
  }
}
