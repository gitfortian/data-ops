package io.yak.ops.business.audit;

import java.util.List;

/** Persistence-framework-neutral page used by the audit read side. */
public record AuditPage<T>(List<T> records, long total, int page, int size) {
  public AuditPage {
    records = records == null ? List.of() : List.copyOf(records);
    page = page <= 0 ? 1 : page;
    size = size <= 0 ? 20 : size;
  }
}
