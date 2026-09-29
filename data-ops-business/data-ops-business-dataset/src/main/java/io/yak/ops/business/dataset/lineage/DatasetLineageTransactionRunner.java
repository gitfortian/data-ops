package io.yak.ops.business.dataset.lineage;

import io.yak.ops.business.dataset.DatasetDetail;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Runs derived lineage synchronization in an independent transaction after the business commit. */
@Component
public class DatasetLineageTransactionRunner {

  private final DatasetLineageSynchronizer synchronizer;

  public DatasetLineageTransactionRunner(DatasetLineageSynchronizer synchronizer) {
    this.synchronizer = synchronizer;
  }

  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      propagation = Propagation.REQUIRES_NEW)
  public void sync(DatasetDetail detail) {
    synchronizer.syncCurrent(detail);
  }
}
