package io.yak.ops.business.audit;

import java.util.List;

/** Filter catalog derived from persisted operation snapshots. */
public record AuditFilterOptions(
    List<AuditFilterOption> actors,
    List<AuditFilterOption> projects,
    List<AuditFilterOption> operationTypes,
    List<AuditFilterOption> resourceTypes,
    List<AuditFilterOption> statuses,
    List<AuditFilterOption> sources) {

  public AuditFilterOptions {
    actors = immutable(actors);
    projects = immutable(projects);
    operationTypes = immutable(operationTypes);
    resourceTypes = immutable(resourceTypes);
    statuses = immutable(statuses);
    sources = immutable(sources);
  }

  public static AuditFilterOptions empty() {
    return new AuditFilterOptions(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
  }

  private static List<AuditFilterOption> immutable(List<AuditFilterOption> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
