package io.yak.ops.business.metric.publication;

import io.yak.ops.business.metric.catalog.MetricCatalogService;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.impact.MetricImpactService;
import io.yak.ops.business.metric.impact.MetricImpactService.DependencyChange;
import io.yak.ops.business.metric.impact.MetricImpactService.DependencyHealth;
import io.yak.ops.business.metric.publication.MetricPublicationGate.GateEvidence;
import io.yak.ops.business.metric.publication.MetricPublicationGate.GateStatus;
import io.yak.ops.business.metric.publication.MetricPublicationGate.PublicationSubject;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Publication gate for the dependency snapshot attached to the current editable Metric version.
 *
 * <p>The current dependency registry is mutable authoring state, therefore it is only valid proof
 * for the current Metric version. A request to publish an older version is explicitly BLOCKED as
 * stale instead of evaluating that old immutable version against today's dependency registry.
 * Provider UNAVAILABLE remains distinct from known OUTDATED/REMOVED business blockers.
 */
@Component
@Order(20)
public class MetricDependencyPublicationGate implements MetricPublicationGate {

  public static final String PROVIDER = "metric-dependency-health-gate/v1";

  private final MetricCatalogService catalogService;
  private final MetricImpactService impactService;

  public MetricDependencyPublicationGate(
      MetricCatalogService catalogService,
      MetricImpactService impactService) {
    this.catalogService = catalogService;
    this.impactService = impactService;
  }

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public GateEvidence evaluate(PublicationSubject subject) {
    Metric current = catalogService.get(subject.metricId());
    if (current.version() != subject.metricVersion()) {
      return GateEvidence.blocked(
          PROVIDER,
          null,
          List.of(
              "STALE_METRIC_VERSION: requested v" + subject.metricVersion()
                  + ", current editable v" + current.version()));
    }

    List<DependencyChange> changes = impactService.dependencyContext(current).changes();
    List<String> issues = new ArrayList<>();
    boolean unavailable = false;
    boolean blocked = false;

    for (DependencyChange change : changes) {
      DependencyHealth health = change.dependencyHealth();
      if (health == DependencyHealth.UNAVAILABLE) {
        unavailable = true;
        issues.add(issue(change, "UNAVAILABLE"));
      } else if (health == DependencyHealth.OUTDATED) {
        blocked = true;
        issues.add(issue(change, "OUTDATED"));
      } else if (health == DependencyHealth.REMOVED) {
        blocked = true;
        issues.add(issue(change, "REMOVED"));
      }
    }

    if (unavailable) {
      return new GateEvidence(PROVIDER, GateStatus.UNAVAILABLE, null, issues);
    }
    if (blocked) {
      return GateEvidence.blocked(PROVIDER, null, issues);
    }
    return GateEvidence.ready(
        PROVIDER,
        "metric-dependency-snapshot:" + subject.metricId() + ":v" + subject.metricVersion());
  }

  private static String issue(DependencyChange change, String state) {
    return change.dependencyType() + ":" + change.dependencyId() + ":" + state;
  }
}
