package io.yak.ops.business.metadata.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.metadata.api.EntityDTO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PhysicalScopeEvidenceQueryServiceTest {
  private final CatalogQueryService catalog = mock(CatalogQueryService.class);
  // Use the genuine interface default methods; a plain Mockito mock masks requireProjectId().
  private final AtomicReference<Optional<ProjectContext>> project =
      new AtomicReference<>(Optional.of(new ProjectContext(31L, "source-scope")));
  private final CurrentProject current = () -> project.get();
  private final PhysicalScopeEvidenceQueryService service =
      new PhysicalScopeEvidenceQueryService(catalog, current);

  private static EntityDTO table(long id, String key, String capture, String hash, int columns) {
    return new EntityDTO(id, "table", Map.of("assetKey", key, "providerType", "HARVESTED",
        "sourceType", "METADATA", "dataSourceId", "source-1", "databaseName", "warehouse",
        "schemaName", "public", "tableName", key,
        "collectJobId", capture, "lastCollectAt", "2026-10-10T08:00:00",
        "contentHash", hash), Map.of("columnCount", columns), Map.of());
  }

  private static EntityDTO column(long id, long parentId, String table, String name, String hash) {
    return new EntityDTO(id, "tableColumn", Map.of("assetKey", table + ":" + name,
        "providerType", "HARVESTED", "dataSourceId", "source-1",
        "databaseName", "warehouse", "schemaName", "public", "tableName", table,
        "columnName", name, "parentAssetId", parentId, "contentHash", hash),
        Map.of("dataType", "bigint", "nullable", false), Map.of());
  }

  @Test void completeSelectionUsesCanonicalCatalogOnlyAndStableFingerprint() {
    when(catalog.byAssetKey("a")).thenReturn(Optional.of(table(11,"a","capture-1","t1",2)));
    when(catalog.byAssetKey("b")).thenReturn(Optional.of(table(12,"b","capture-1","t2",1)));
    when(catalog.childrenBounded(11,"tableColumn",501))
        .thenReturn(List.of(column(112,11,"a","id","c1"),column(111,11,"a","amount","c2")));
    when(catalog.childrenBounded(12,"tableColumn",501))
        .thenReturn(List.of(column(121,12,"b","buyer","c3")));
    var result = service.readSelectedTables(List.of("b", "a"));
    assertEquals(31L,result.projectId());
    assertEquals("source-1",result.dataSourceId());
    assertEquals("capture-1",result.collectJobId());
    assertEquals(List.of("a","b"),result.tables().stream().map(t -> t.assetKey()).toList());
    assertEquals(List.of("amount","id"),result.tables().get(0).columns().stream()
        .map(col -> col.name()).toList());
    assertEquals(64,result.fingerprint().length());
    assertEquals(result.fingerprint(),service.readSelectedTables(List.of("a","b")).fingerprint());
    verify(catalog,atLeastOnce()).childrenBounded(11,"tableColumn",501);
    verify(catalog,never()).children(11,"tableColumn");
  }

  @Test void missingOrIncompleteSourceIsBlockedNotInvented() {
    assertThrows(IllegalArgumentException.class, () -> service.readSelectedTables(List.of()));
    assertThrows(IllegalArgumentException.class, () -> service.readSelectedTables(List.of("a","a")));
    assertThrows(IllegalStateException.class, () -> service.readSelectedTables(List.of("not-collected")));
    when(catalog.byAssetKey("a")).thenReturn(Optional.of(table(11,"a","capture-1","t1",2)));
    when(catalog.childrenBounded(11,"tableColumn",501))
        .thenReturn(List.of(column(111,11,"a","id","c1")));
    assertTrue(assertThrows(IllegalStateException.class,
        () -> service.readSelectedTables(List.of("a"))).getMessage()
        .contains("COLUMN_COVERAGE_MISMATCH"));
  }

  @Test void mixedCollectionRunsFailClosed() {
    when(catalog.byAssetKey("a")).thenReturn(Optional.of(table(11,"a","capture-1","t1",1)));
    when(catalog.byAssetKey("b")).thenReturn(Optional.of(table(12,"b","capture-2","t2",1)));
    when(catalog.childrenBounded(11,"tableColumn",501))
        .thenReturn(List.of(column(111,11,"a","id","c1")));
    assertTrue(assertThrows(IllegalStateException.class,
        () -> service.readSelectedTables(List.of("a","b"))).getMessage()
        .contains("MIXED_CAPTURE_RUN"));
  }

  @Test void incorrectColumnParentOrHashCannotBeApproved() {
    when(catalog.byAssetKey("a")).thenReturn(Optional.of(table(11,"a","capture-1","t1",1)));
    when(catalog.childrenBounded(11,"tableColumn",501))
        .thenReturn(List.of(column(111,99,"a","id","c1")));
    assertTrue(assertThrows(IllegalStateException.class,
        () -> service.readSelectedTables(List.of("a"))).getMessage()
        .contains("INVALID_COLUMN_PROJECTION"));
    when(catalog.childrenBounded(11,"tableColumn",501))
        .thenReturn(List.of(column(111,11,"a","id","c1")));
    var evidence = service.readSelectedTables(List.of("a"));
    when(catalog.childrenBounded(11,"tableColumn",501))
        .thenReturn(List.of(column(111,11,"a","id","changed")));
    assertNotEquals(evidence.fingerprint(), service.readSelectedTables(List.of("a")).fingerprint());
  }

  @Test void projectContextIsMandatory() {
    project.set(Optional.empty());
    assertThrows(RuntimeException.class, () -> service.readSelectedTables(List.of("a")));
    verifyNoInteractions(catalog);
  }
}
