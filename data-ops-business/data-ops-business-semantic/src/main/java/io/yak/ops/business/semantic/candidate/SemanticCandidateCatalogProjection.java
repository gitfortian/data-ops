package io.yak.ops.business.semantic.candidate;

import cn.dev33.satoken.stp.StpUtil;
import io.yak.ops.business.semantic.api.SemanticCandidateCatalogApi;
import io.yak.ops.business.semantic.catalog.StandardCatalogService;
import io.yak.ops.business.semantic.domain.BusinessDomainService;
import io.yak.ops.business.semantic.field.SemanticFieldService;
import io.yak.ops.business.semantic.process.BusinessProcessService;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
import io.yak.ops.core.project.CurrentProject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Semantic owns catalog lookup, including project scope and enabled status. */
@Component
public final class SemanticCandidateCatalogProjection implements SemanticCandidateCatalogApi {
  private static final int PAGE = 200;
  private static final int CAP = 1200;
  private final CurrentProject project;
  private final BusinessDomainService domains;
  private final BusinessProcessService processes;
  private final SemanticFieldService fields;
  private final StandardCatalogService standards;

  public SemanticCandidateCatalogProjection(CurrentProject project,
      BusinessDomainService domains, BusinessProcessService processes,
      SemanticFieldService fields, StandardCatalogService standards) {
    this.project = project;
    this.domains = domains;
    this.processes = processes;
    this.fields = fields;
    this.standards = standards;
  }

  @Override public Snapshot read() {
    StpUtil.checkPermission(SemanticPermissionCode.READ);
    long projectId = project.requireProjectId();
    List<Entry> entries = new ArrayList<>();
    var queue = new java.util.ArrayDeque<>(domains.tree());
    while (!queue.isEmpty()) {
      if (entries.size() >= CAP) throw new IllegalStateException("[F039_CATALOG_INCOMPLETE]");
      var d = queue.removeFirst();
      entries.add(new Entry("DOMAIN", d.id(), 0, d.code(), d.name(), "ACTIVE", null, null, null));
      queue.addAll(d.children());
    }
    for (int page = 1; ; page++) {
      var result = processes.page(page, PAGE, null, null, null);
      for (var p : result.records())
        entries.add(new Entry("PROCESS", p.id(), 0, p.code(), p.name(), "ACTIVE",
            p.bizType(), null, null));
      checkCap(entries);
      if ((long) page * PAGE >= result.total()) break;
    }
    for (int page = 1; ; page++) {
      var result = fields.page(page, PAGE, null, null);
      for (var f : result.records())
        entries.add(new Entry("FIELD", f.id(), f.version(), f.code(), f.name(), f.status(),
            f.role(), f.stdTypeId(), f.stdUnitId()));
      checkCap(entries);
      if ((long) page * PAGE >= result.total()) break;
    }
    for (int page = 1; ; page++) {
      var result = standards.page(page, PAGE, null, null, null);
      for (var s : result.records())
        entries.add(new Entry(s.getKind(), s.getId(), Objects.requireNonNullElse(s.getVersion(), 0),
            s.getCode(), s.getName(), s.getStatus(), null, null, null));
      checkCap(entries);
      if ((long) page * PAGE >= result.total()) break;
    }
    // Recheck permission; do not return results if access was revoked during pagination.
    StpUtil.checkPermission(SemanticPermissionCode.READ);
    if (project.requireProjectId() != projectId)
      throw new IllegalStateException("[F039_PROJECT_CHANGED]");
    entries.sort(java.util.Comparator.comparing(Entry::kind)
        .thenComparing(e -> Objects.toString(e.code(), ""))
        .thenComparing(e -> Objects.toString(e.id(), "")));
    return new Snapshot(projectId, entries, true);
  }

  private static void checkCap(List<Entry> entries) {
    if (entries.size() > CAP) throw new IllegalStateException("[F039_CATALOG_INCOMPLETE]");
  }
}
