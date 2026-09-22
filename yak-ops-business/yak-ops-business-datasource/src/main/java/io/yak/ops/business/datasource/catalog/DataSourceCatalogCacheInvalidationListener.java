package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.api.DataSourceChangedEvent;
import io.yak.ops.business.datasource.api.DataSourceChangedEvent.ChangeType;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Clears stale local Catalog metadata only after datasource mutations commit successfully. */
@Slf4j
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceCatalogCacheInvalidationListener {

  private final DataSourceCatalogMetadataCache metadataCache;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDataSourceChanged(DataSourceChangedEvent event) {
    // CREATED 无需清缓存：新建的源还没有任何历史条目可失效。
    if (event.changeType() == ChangeType.CREATED) return;
    int invalidated = metadataCache.invalidate(event.dataSourceId());
    if (invalidated > 0) {
      log.debug(
          "Invalidated datasource catalog cache dataSourceId={} changeType={} entries={}",
          event.dataSourceId(),
          event.changeType(),
          invalidated);
    }
  }
}
