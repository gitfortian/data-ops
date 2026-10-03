package io.yak.ops.business.development.domain;

import java.util.Set;
import java.util.Locale;

/** One classification contract for baseline and derived SQL lineage. */
public final class SqlExpressionClassification {
  private SqlExpressionClassification() {}
  private static final Set<String> AGGREGATE_FUNCTIONS = Set.of(
      "AVG", "COUNT", "GROUP_CONCAT", "MAX", "MIN", "SUM",
      "STDDEV", "STDDEV_POP", "STDDEV_SAMP", "VAR_POP", "VAR_SAMP", "VARIANCE");
  public static boolean aggregate(String name) {
    return name != null && AGGREGATE_FUNCTIONS.contains(name.toUpperCase(Locale.ROOT));
  }
}
