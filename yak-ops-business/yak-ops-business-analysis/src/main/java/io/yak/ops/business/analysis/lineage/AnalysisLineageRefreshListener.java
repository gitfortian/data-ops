package io.yak.ops.business.analysis.lineage;

import io.yak.ops.business.analysis.definition.AnalysisChangedEvent;
import io.yak.ops.business.analysis.definition.AnalysisReader;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Keeps derived Analysis lineage aligned with committed Analysis definition mutations. */
@Component
public class AnalysisLineageRefreshListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisLineageRefreshListener.class);

  private final AnalysisReader reader;
  private final AnalysisLineageSynchronizer lineage;
  private final ProjectContextScope projectContextScope;

  public AnalysisLineageRefreshListener(
      AnalysisReader reader,
      AnalysisLineageSynchronizer lineage,
      ProjectContextScope projectContextScope) {
    this.reader = reader;
    this.lineage = lineage;
    this.projectContextScope = projectContextScope;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void refresh(AnalysisChangedEvent event) {
    if (event == null || event.projectId() <= 0L || event.analysisId() <= 0L) return;
    try {
      projectContextScope.run(
          new ProjectContext(event.projectId(), null),
          () -> refreshWithinProject(event));
    } catch (RuntimeException exception) {
      LOGGER.warn(
          "Analysis lineage refresh failed after commit for project {} analysis {}: {}",
          event.projectId(),
          event.analysisId(),
          exception.getMessage(),
          exception);
    }
  }

  private void refreshWithinProject(AnalysisChangedEvent event) {
    if (event.deleted()) lineage.clear(event.analysisId());
    else lineage.syncCurrent(reader.require(event.analysisId()));
  }
}
