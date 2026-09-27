package io.yak.ops.business.metric.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metric.catalog.MetricCatalogService;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricStatus;
import io.yak.ops.business.metric.domain.MetricType;
import io.yak.ops.business.metric.domain.StatPeriod;
import io.yak.ops.business.metric.impact.MetricImpactService;
import io.yak.ops.business.metric.impact.MetricImpactService.AuthoringNextStep;
import io.yak.ops.business.metric.impact.MetricImpactService.DependencyChange;
import io.yak.ops.business.metric.impact.MetricImpactService.DependencyContext;
import io.yak.ops.business.metric.impact.MetricImpactService.DependencyHealth;
import io.yak.ops.business.metric.publication.MetricPublicationGate.GateStatus;
import io.yak.ops.business.metric.publication.MetricPublicationGate.PublicationSubject;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricDependencyPublicationGateTest {

  private final MetricCatalogService catalogService = mock(MetricCatalogService.class);
  private final MetricImpactService impactService = mock(MetricImpactService.class);
  private final MetricDependencyPublicationGate gate =
      new MetricDependencyPublicationGate(catalogService, impactService);

  @Test
  void blocksStaleVersionBeforeReadingMutableDependencyRegistry() {
    PublicationSubject subject = subject(3);
    when(catalogService.get(7L)).thenReturn(metric(4));

    var evidence = gate.evaluate(subject);

    assertThat(evidence.status()).isEqualTo(GateStatus.BLOCKED);
    assertThat(evidence.issues()).singleElement().asString().contains("STALE_METRIC_VERSION");
    verify(impactService, never()).dependencyContext(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void blocksKnownOutdatedAndRemovedDependencies() {
    Metric metric = metric(3);
    when(catalogService.get(7L)).thenReturn(metric);
    when(impactService.dependencyContext(metric)).thenReturn(new DependencyContext(
        List.of(
            change("MODEL", 11L, DependencyHealth.OUTDATED),
            change("CALIBER", 12L, DependencyHealth.REMOVED)),
        AuthoringNextStep.RESOLVE_REMOVED_DEPENDENCY));

    var evidence = gate.evaluate(subject(3));

    assertThat(evidence.status()).isEqualTo(GateStatus.BLOCKED);
    assertThat(evidence.issues()).containsExactly(
        "MODEL:11:OUTDATED",
        "CALIBER:12:REMOVED");
  }

  @Test
  void keepsProviderUnavailableDistinctFromBusinessBlockers() {
    Metric metric = metric(3);
    when(catalogService.get(7L)).thenReturn(metric);
    when(impactService.dependencyContext(metric)).thenReturn(new DependencyContext(
        List.of(
            change("MODEL", 11L, DependencyHealth.OUTDATED),
            change("UNIT", 13L, DependencyHealth.UNAVAILABLE)),
        AuthoringNextStep.RETRY_DEPENDENCY_PROVIDER));

    var evidence = gate.evaluate(subject(3));

    assertThat(evidence.status()).isEqualTo(GateStatus.UNAVAILABLE);
    assertThat(evidence.issues()).containsExactly(
        "MODEL:11:OUTDATED",
        "UNIT:13:UNAVAILABLE");
  }

  @Test
  void becomesReadyWhenCurrentVersionDependenciesAreUpToDate() {
    Metric metric = metric(3);
    when(catalogService.get(7L)).thenReturn(metric);
    when(impactService.dependencyContext(metric)).thenReturn(new DependencyContext(
        List.of(
            change("MODEL", 11L, DependencyHealth.UP_TO_DATE),
            change("UNIT", 13L, DependencyHealth.UP_TO_DATE)),
        AuthoringNextStep.VALIDATE));

    var evidence = gate.evaluate(subject(3));

    assertThat(evidence.status()).isEqualTo(GateStatus.READY);
    assertThat(evidence.evidenceRef()).isEqualTo("metric-dependency-snapshot:7:v3");
    assertThat(evidence.issues()).isEmpty();
  }

  private static DependencyChange change(
      String type, Long id, DependencyHealth health) {
    return new DependencyChange(
        type,
        id,
        type + "_" + id,
        1,
        health == DependencyHealth.REMOVED || health == DependencyHealth.UNAVAILABLE ? null : 2,
        health == DependencyHealth.UP_TO_DATE ? "UNCHANGED" : "CHANGED",
        health);
  }

  private static PublicationSubject subject(int version) {
    return new PublicationSubject(7L, 31L, version, "sha-" + version);
  }

  private static Metric metric(int version) {
    LocalDateTime now = LocalDateTime.of(2026, 9, 27, 12, 0);
    return new Metric(
        7L,
        "gmv",
        "GMV",
        1L,
        2L,
        MetricType.ATOMIC,
        12L,
        "SUM(amount)",
        "SUM(amount)",
        null,
        null,
        null,
        null,
        null,
        11L,
        "shop_id",
        StatPeriod.DAY,
        13L,
        "gross merchandise value",
        "owner",
        MetricStatus.ENABLED,
        version,
        "tester",
        "tester",
        now,
        now);
  }
}
