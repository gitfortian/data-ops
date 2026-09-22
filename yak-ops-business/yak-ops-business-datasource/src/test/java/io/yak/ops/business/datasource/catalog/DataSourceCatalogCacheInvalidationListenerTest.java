package io.yak.ops.business.datasource.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.api.DataSourceChangedEvent;
import io.yak.ops.business.datasource.api.DataSourceChangedEvent.ChangeType;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DataSourceCatalogCacheInvalidationListenerTest {

  @Test
  void datasourceChangeRemovesOnlyMatchingCacheEntries() {
    DataSourceCatalogMetadataCache cache = new DataSourceCatalogMetadataCache();
    DataSourceDefinition first = definition(1L);
    DataSourceDefinition second = definition(2L);

    cache.getOrLoad(cache.key(first, "tables"), 60, () -> "first");
    cache.getOrLoad(cache.key(second, "tables"), 60, () -> "second");
    assertThat(cache.size()).isEqualTo(2);

    new DataSourceCatalogCacheInvalidationListener(cache)
        .onDataSourceChanged(event(1L, ChangeType.UPDATED));

    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.invalidate(1L)).isZero();
    assertThat(cache.invalidate(2L)).isEqualTo(1);
  }

  @Test
  void deleteInvalidatesButCreateKeepsCacheUntouched() {
    DataSourceCatalogMetadataCache cache = new DataSourceCatalogMetadataCache();
    DataSourceDefinition existing = definition(1L);
    cache.getOrLoad(cache.key(existing, "tables"), 60, () -> "first");

    new DataSourceCatalogCacheInvalidationListener(cache)
        .onDataSourceChanged(event(1L, ChangeType.CREATED));
    assertThat(cache.size()).isEqualTo(1);

    new DataSourceCatalogCacheInvalidationListener(cache)
        .onDataSourceChanged(event(1L, ChangeType.DELETED));
    assertThat(cache.size()).isZero();
  }

  private DataSourceChangedEvent event(Long dataSourceId, ChangeType changeType) {
    return new DataSourceChangedEvent(
        dataSourceId, DataSourceDbType.MYSQL, "orders-db", changeType);
  }

  private DataSourceDefinition definition(Long id) {
    DataSourceDefinition definition = mock(DataSourceDefinition.class);
    when(definition.getId()).thenReturn(id);
    when(definition.getUpdateTime()).thenReturn(LocalDateTime.of(2026, 8, 27, 14, 0));
    return definition;
  }
}
