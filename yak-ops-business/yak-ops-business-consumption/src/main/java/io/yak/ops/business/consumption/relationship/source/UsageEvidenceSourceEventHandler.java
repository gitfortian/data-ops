package io.yak.ops.business.consumption.relationship.source;

import io.yak.ops.business.consumption.relationship.UsageNormalizationResult;
import io.yak.ops.business.dataset.DatasetSuccessfulQueryEvent;
import io.yak.ops.business.dataservice.domain.DataServiceSuccessfulInvocationEvent;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Normalizes source-owned success events after their source audit transaction commits. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class UsageEvidenceSourceEventHandler {

  private static final Logger LOG = LoggerFactory.getLogger(UsageEvidenceSourceEventHandler.class);

  private final DatasetUsageEvidenceNormalizer datasetNormalizer;
  private final DataServiceUsageEvidenceNormalizer dataServiceNormalizer;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onDatasetQuery(DatasetSuccessfulQueryEvent event) {
    report(datasetNormalizer.normalize(event == null ? null : event.projectId(), event));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onDataServiceInvocation(DataServiceSuccessfulInvocationEvent event) {
    report(dataServiceNormalizer.normalize(event));
  }

  private void report(UsageNormalizationResult result) {
    if (result == null || (result.state() != io.yak.ops.business.consumption.relationship.UsageNormalizationState.GAP
        && result.state() != io.yak.ops.business.consumption.relationship.UsageNormalizationState.UNAVAILABLE)) {
      return;
    }
    LOG.warn("Successful source usage could not be normalized: providerEvidenceRef={}, state={}, reason={}",
        result.providerEvidenceRef(), result.state(), result.message());
  }
}
