package io.yak.ops.business.dataset.lineage;

import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Refreshes derived Dataset lineage only after the Dataset business transaction has committed. */
@Component
public class DatasetLineageRefreshListener {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DatasetLineageRefreshListener.class);

  private final DatasetLineageSnapshotReader snapshotReader;
  private final DatasetLineageTransactionRunner transactionRunner;
  private final ProjectContextScope projectScope;

  public DatasetLineageRefreshListener(
      DatasetLineageSnapshotReader snapshotReader,
      DatasetLineageTransactionRunner transactionRunner,
      ProjectContextScope projectScope) {
    this.snapshotReader = snapshotReader;
    this.transactionRunner = transactionRunner;
    this.projectScope = projectScope;
  }

  @TransactionalEventListener(
      phase = TransactionPhase.AFTER_COMMIT,
      fallbackExecution = true)
  public void refresh(DatasetLineageRefreshRequested event) {
    if (event == null || event.projectId() <= 0L || event.datasetId() <= 0L) {
      return;
    }
    try {
      projectScope.run(
          new ProjectContext(event.projectId(), null),
          () -> transactionRunner.sync(snapshotReader.require(event.datasetId())));
    } catch (RuntimeException exception) {
      LOGGER.warn(
          "Dataset lineage refresh failed after commit for project {} dataset {}: {}",
          event.projectId(),
          event.datasetId(),
          exception.getMessage(),
          exception);
    }
  }
}
