package io.yak.ops.business.consumption.product.provider.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataServiceAssetProviderTest {
  @Test
  void projectMismatchAndInvalidCursorCannotEnterSourceRead() {
    var reader = mock(DataServiceReader.class);
    var project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(7L);
    var provider = new DataServiceAssetProvider(reader, project);
    assertThrows(IllegalArgumentException.class,
        () -> provider.cursorList(new AssetCursorQuery(8L, null, null, 500)));
    assertThrows(IllegalArgumentException.class,
        () -> provider.cursorList(new AssetCursorQuery(7L, null, "invalid", 500)));
    verifyNoInteractions(reader);
  }

  @Test
  void boundedCursorAndRefreshShareStableSourceKeyWithoutRuntimeSecrets() {
    var reader = mock(DataServiceReader.class);
    var project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(7L);
    var source = mock(DataServiceDefinition.class);
    when(source.id()).thenReturn(42L);
    when(source.settings()).thenReturn(
        new DataServiceSettings("Orders", "/orders", 10, 30, true, "Sample", false));
    when(source.sourceReference()).thenReturn(new SourceReference("DATA_DEVELOPMENT_DATA_SERVICE", "99", 101L, 3));
    when(source.runtimeGeneration()).thenReturn(5L);
    when(source.updateTime()).thenReturn(LocalDateTime.of(2026, 10, 4, 10, 0));
    when(reader.cursorList(41L, null, 1)).thenReturn(List.of(source));
    when(reader.find(42L)).thenReturn(Optional.of(source));
    var provider = new DataServiceAssetProvider(reader, project);

    var page = provider.cursorList(new AssetCursorQuery(7L, null, "41", 1));
    var descriptor = page.items().getFirst();
    assertEquals("42", page.nextCursor());
    assertEquals("data_service:42", descriptor.assetKey());
    assertEquals("42", descriptor.sourceId());
    assertEquals(descriptor.assetKey(), provider.refresh("42").orElseThrow().assetKey());
    assertFalse(descriptor.extra().containsKey("sql"));
    assertFalse(descriptor.extra().containsKey("dataSourceId"));
    verify(reader).cursorList(41L, null, 1);

    when(source.settings()).thenReturn(
        new DataServiceSettings("Renamed", "/renamed", 10, 30, false, "Updated", false));
    when(source.sourceReference()).thenReturn(new SourceReference("DATA_DEVELOPMENT_DATA_SERVICE", "99", 102L, 4));
    var refreshed = provider.refresh("42").orElseThrow();
    assertEquals(descriptor.assetKey(), refreshed.assetKey());
    assertNotEquals(descriptor.contentHash(), refreshed.contentHash());
  }
}
