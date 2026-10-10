package io.yak.ops.business.agent.gateway;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.SourceSemanticCatalog;
import io.yak.ops.business.semantic.api.SemanticCandidateCatalogApi;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Only this gateway knows Semantic's read-only projection. Never imports the
 * Semantic repository in Agent or participates in Semantic business writes.
 */
@Component
@ConditionalOnAgentEnabled
public final class SourceSemanticCatalogGateway implements SourceSemanticCatalog {
  private final SemanticCandidateCatalogApi delegate;
  public SourceSemanticCatalogGateway(SemanticCandidateCatalogApi delegate) {
    this.delegate = delegate;
  }
  @Override public Snapshot read() {
    var result = delegate.read();
    List<Entry> entries = result.entries().stream().map(e -> new Entry(e.kind(),e.id(),
        e.version(),e.code(),e.name(),e.status(),e.role(),e.typeId(),e.unitId())).toList();
    return new Snapshot(result.projectId(),entries,result.complete());
  }
}
