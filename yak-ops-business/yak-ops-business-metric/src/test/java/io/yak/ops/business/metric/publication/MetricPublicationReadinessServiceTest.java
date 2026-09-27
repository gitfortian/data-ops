package io.yak.ops.business.metric.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.publication.MetricPublicationGate.GateEvidence;
import io.yak.ops.business.metric.publication.MetricPublicationGate.GateStatus;
import io.yak.ops.business.metric.publication.MetricPublicationReadinessService.ReadinessStatus;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.common.bean.po.metric.MetricVersionPO;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MetricPublicationReadinessServiceTest {

  private MetricVersionRepository versionRepository;
  private ObjectProvider<MetricPublicationGate> gateProvider;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    versionRepository = mock(MetricVersionRepository.class);
    gateProvider = mock(ObjectProvider.class);
  }

  @Test
  void noGateProviderFailsClosed() {
    when(versionRepository.findByMetricAndVersion(10L, 3)).thenReturn(version(301L, 3));
    when(gateProvider.orderedStream()).thenReturn(Stream.empty());
    MetricPublicationReadinessService service = service();

    var readiness = service.check(10L, 3);

    assertThat(readiness.status()).isEqualTo(ReadinessStatus.BLOCKED);
    assertThat(readiness.subject().metricVersionId()).isEqualTo(301L);
    assertThat(readiness.subject().snapshotDigest()).hasSize(64);
    assertThat(readiness.gates()).singleElement().satisfies(gate -> {
      assertThat(gate.status()).isEqualTo(GateStatus.UNAVAILABLE);
      assertThat(gate.provider())
          .isEqualTo(MetricPublicationReadinessService.GATE_PROVIDER_UNAVAILABLE);
    });
  }

  @Test
  void allRegisteredGatesMustBeReady() {
    when(versionRepository.findByMetricAndVersion(10L, 3)).thenReturn(version(301L, 3));
    MetricPublicationGate definition = gate(
        "definition-validation",
        GateEvidence.ready("definition-validation", "metric-validation:9001"));
    MetricPublicationGate dependency = gate(
        "dependency-health",
        GateEvidence.ready("dependency-health", "dependency-check:77"));
    when(gateProvider.orderedStream()).thenReturn(Stream.of(definition, dependency));

    var readiness = service().check(10L, 3);

    assertThat(readiness.status()).isEqualTo(ReadinessStatus.READY);
    assertThat(readiness.gates()).hasSize(2).allMatch(g -> g.status() == GateStatus.READY);
  }

  @Test
  void oneBlockedGateBlocksPublicationReadiness() {
    when(versionRepository.findByMetricAndVersion(10L, 3)).thenReturn(version(301L, 3));
    MetricPublicationGate definition = gate(
        "definition-validation",
        GateEvidence.ready("definition-validation", "metric-validation:9001"));
    MetricPublicationGate dependency = gate(
        "dependency-health",
        GateEvidence.blocked(
            "dependency-health",
            "dependency-check:78",
            List.of("Referenced model is outdated")));
    when(gateProvider.orderedStream()).thenReturn(Stream.of(definition, dependency));

    var readiness = service().check(10L, 3);

    assertThat(readiness.status()).isEqualTo(ReadinessStatus.BLOCKED);
    assertThat(readiness.gates()).extracting(GateEvidence::status)
        .containsExactly(GateStatus.READY, GateStatus.BLOCKED);
  }

  @Test
  void providerFailureBecomesUnavailableAndFailsClosed() {
    when(versionRepository.findByMetricAndVersion(10L, 3)).thenReturn(version(301L, 3));
    MetricPublicationGate broken = mock(MetricPublicationGate.class);
    when(broken.provider()).thenReturn("definition-validation");
    when(broken.evaluate(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new IllegalStateException("validation store unavailable"));
    when(gateProvider.orderedStream()).thenReturn(Stream.of(broken));

    var readiness = service().check(10L, 3);

    assertThat(readiness.status()).isEqualTo(ReadinessStatus.BLOCKED);
    assertThat(readiness.gates()).singleElement().satisfies(gate -> {
      assertThat(gate.status()).isEqualTo(GateStatus.UNAVAILABLE);
      assertThat(gate.issues()).contains("validation store unavailable");
    });
  }

  @Test
  void missingMetricVersionCannotBeConsideredForPublication() {
    when(versionRepository.findByMetricAndVersion(404L, 1)).thenReturn(null);

    assertThatThrownBy(() -> service().check(404L, 1))
        .isInstanceOf(MetricException.class)
        .hasMessageContaining("版本 v1 不存在");
  }

  private MetricPublicationReadinessService service() {
    return new MetricPublicationReadinessService(versionRepository, gateProvider);
  }

  private static MetricPublicationGate gate(String provider, GateEvidence evidence) {
    MetricPublicationGate gate = mock(MetricPublicationGate.class);
    when(gate.provider()).thenReturn(provider);
    when(gate.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(evidence);
    return gate;
  }

  private static MetricVersionPO version(Long id, int version) {
    MetricVersionPO po = new MetricVersionPO();
    po.setId(id);
    po.setMetricId(10L);
    po.setVersion(version);
    po.setSnapshot("{\"metricCode\":\"GMV\",\"version\":" + version + "}");
    return po;
  }
}
