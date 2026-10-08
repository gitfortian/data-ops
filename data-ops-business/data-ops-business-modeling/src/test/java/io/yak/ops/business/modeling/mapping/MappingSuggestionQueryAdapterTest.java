package io.yak.ops.business.modeling.mapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.common.constant.datasource.DataSourcePermissionCode;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MappingSuggestionQueryAdapterTest {
  private final MappingService mappings = mock(MappingService.class);
  private final DataSourceCatalogReader catalog = mock(DataSourceCatalogReader.class);
  private final ActionAuthorization authorization = mock(ActionAuthorization.class);
  private final MappingSuggestionQueryAdapter adapter = new MappingSuggestionQueryAdapter(mappings, catalog, authorization);

  @Test void forbiddenSourceStopsBeforeAnyModelOrCatalogRead() {
    doThrow(new IllegalArgumentException("forbidden")).when(authorization).requirePermission(DataSourcePermissionCode.READ);
    assertThrows(IllegalArgumentException.class, () -> adapter.require(7, "user_id", 9, "db", "users", ""));
    verifyNoInteractions(mappings, catalog);
  }
  @Test void boundedStablePoolSignalsTruncationAndFreshMetadata() {
    setup();
    when(catalog.listColumnsFresh(9L, "db", null, "users")).thenReturn(IntStream.range(0, 60)
        .mapToObj(i -> column("c" + i, "x".repeat(600))).toList());
    var pool = adapter.require(7, "user_id", 9, "db", "users", "");
    assertEquals(50, pool.columns().size()); assertTrue(pool.truncated());
    assertEquals(512, pool.columns().getFirst().description().length());
    assertEquals(64, pool.sourceDefinition().length()); verify(catalog, never()).listColumns(any(), any(), any(), any());
    var filtered = adapter.require(7, "user_id", 9, "db", "users", "c59");
    assertEquals(1, filtered.columns().size()); assertFalse(filtered.truncated());
    assertEquals(pool.sourceDefinition(), filtered.sourceDefinition());
  }
  @Test void unavailableCatalogIsNotAnEmptyPool() {
    setup(); when(catalog.listColumnsFresh(9L, "db", null, "users")).thenThrow(new IllegalStateException("unavailable"));
    assertThrows(IllegalStateException.class, () -> adapter.require(7, "user_id", 9, "db", "users", ""));
  }
  @Test void invalidSelectionStopsBeforeReads() {
    assertThrows(IllegalArgumentException.class, () -> adapter.require(7, "user_id", 9, " ", "users", ""));
    verifyNoInteractions(mappings, catalog);
  }
  private void setup() {
    var field = new ColumnDefinition(1L, "user_id", "BIGINT", null, null, false, null, "用户编号", null, 0);
    when(mappings.editContext(7L, "user_id")).thenReturn(new MappingService.EditContext("用户", "MYSQL", field,
        new MappingService.MappingView("user_id", "BIGINT", false, null, null, null, null, null, null), "a".repeat(64)));
  }
  private CatalogColumn column(String name, String description) { return new CatalogColumn(name, "BIGINT", -5, 20, 0, false, 1, false, description); }
}
