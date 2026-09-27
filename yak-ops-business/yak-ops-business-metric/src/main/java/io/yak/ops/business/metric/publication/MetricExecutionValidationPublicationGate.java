package io.yak.ops.business.metric.publication;

import io.yak.ops.business.metric.publication.MetricPublicationGate.GateEvidence;
import io.yak.ops.business.metric.publication.MetricPublicationGate.PublicationSubject;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Explicit Phase 5 contract for data/execution validation.
 *
 * <p>Metric is a governed business definition, not a new query/OLAP runtime. The platform currently
 * has no standalone Metric execution provider that can truthfully execute an immutable MetricVersion.
 * Returning NOT_APPLICABLE makes that absence visible and auditable instead of fabricating a READY
 * execution check or abusing Dataset/Data Service runtime as a second Metric engine. Actual governed
 * Query/Preview/Invoke remains owned by Phase 4 source products.
 */
@Component
@Order(30)
public class MetricExecutionValidationPublicationGate implements MetricPublicationGate {

  public static final String PROVIDER = "metric-execution-validation/v1";
  public static final String REASON =
      "No standalone Metric execution runtime is defined; governed execution remains owned by "
          + "Dataset/Data Service consumption targets";

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public GateEvidence evaluate(PublicationSubject subject) {
    return GateEvidence.notApplicable(PROVIDER, REASON);
  }
}
