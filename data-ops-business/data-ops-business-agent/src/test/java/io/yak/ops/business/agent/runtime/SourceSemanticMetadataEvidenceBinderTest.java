package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.yak.ops.business.metadata.api.PhysicalScopeEvidenceQueryApi;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class SourceSemanticMetadataEvidenceBinderTest {
  private final PhysicalScopeEvidenceQueryApi metadata = mock(PhysicalScopeEvidenceQueryApi.class);
  private final AtomicBoolean allowed = new AtomicBoolean(true);
  private final SourceSemanticMetadataEvidenceBinder binder =
      new SourceSemanticMetadataEvidenceBinder(metadata, (project, source) -> {
        if (project != 31 || !"7".equals(source) || !allowed.get())
          throw new IllegalArgumentException("[F039_DATASOURCE_ACCESS_DENIED]");
      });

  private PhysicalScopeEvidenceQueryApi.Evidence evidence(String hash) {
    return new PhysicalScopeEvidenceQueryApi.Evidence(31,"7","warehouse","public","capture-1",
        "2026-10-10T12:00:00","evidence",List.of(
        new PhysicalScopeEvidenceQueryApi.Table("orders","orders",hash,2,List.of(
          new PhysicalScopeEvidenceQueryApi.Column("amount","hash-amount","bigint",false,""),
          new PhysicalScopeEvidenceQueryApi.Column("id","hash-id","bigint",true,"")))));
  }

  @Test void boundedCompleteEvidenceBecomesImmutableColumnBoundScope() {
    when(metadata.readSelectedTables(List.of("orders"))).thenReturn(evidence("table-hash"));
    var scope=binder.bind(31,List.of("orders"));
    assertEquals("capture-1",scope.captureId());
    assertEquals(List.of("amount@hash-amount","id@hash-id"),scope.tables().get(0).columns());
    assertEquals(scope,binder.recheck(scope));
    verify(metadata,times(2)).readSelectedTables(List.of("orders"));
  }

  @Test void permissionRevocationStopsRecheckAndNoScopeIsIssued() {
    when(metadata.readSelectedTables(List.of("orders"))).thenReturn(evidence("table-hash"));
    var scope=binder.bind(31,List.of("orders"));
    allowed.set(false);
    assertThrows(IllegalArgumentException.class,()->binder.recheck(scope));
  }

  @Test void staleHashOrMissingSelectionNeverBecomesAuthorized() {
    when(metadata.readSelectedTables(List.of("orders"))).thenReturn(evidence("table-hash"));
    var scope=binder.bind(31,List.of("orders"));
    when(metadata.readSelectedTables(List.of("orders"))).thenReturn(evidence("changed"));
    assertThrows(IllegalStateException.class,()->binder.recheck(scope));
    assertThrows(IllegalArgumentException.class,()->binder.bind(31,List.of()));
    assertThrows(IllegalArgumentException.class,()->binder.bind(0,List.of("orders")));
  }

  @Test void crossProjectAndIncompleteSourceFailClosed() {
    when(metadata.readSelectedTables(List.of("orders"))).thenReturn(
        new PhysicalScopeEvidenceQueryApi.Evidence(32,"7","warehouse","public","capture-1",
        "time","fingerprint",List.of()));
    assertThrows(IllegalStateException.class,()->binder.bind(31,List.of("orders")));
    when(metadata.readSelectedTables(List.of("orders"))).thenReturn(
        new PhysicalScopeEvidenceQueryApi.Evidence(31,"7","warehouse","public","capture-1",
        "time","fingerprint",List.of()));
    assertThrows(IllegalStateException.class,()->binder.bind(31,List.of("orders")));
  }
}
