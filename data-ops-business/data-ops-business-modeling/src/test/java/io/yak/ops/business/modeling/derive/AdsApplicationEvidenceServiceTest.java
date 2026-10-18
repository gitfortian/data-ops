package io.yak.ops.business.modeling.derive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.yak.ops.business.modeling.derive.AdsApplicationDesignService.Contract;
import io.yak.ops.business.modeling.derive.AdsApplicationDesignService.MetricPublicationPin;
import io.yak.ops.business.modeling.derive.AdsApplicationDesignService.Saved;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Evidence;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Reference;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Status;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdsApplicationEvidenceServiceTest {

  private final AdsApplicationDesignService contracts = mock(AdsApplicationDesignService.class);
  private final MetricPublicationEvidenceProvider metric = mock(MetricPublicationEvidenceProvider.class);
  private final AdsApplicationEvidenceService service =
      new AdsApplicationEvidenceService(contracts, metric);

  @Test
  void reportsUnconfiguredMetricAndDisconnectedDomainsWithoutGuessing() {
    when(contracts.inspect(10L)).thenReturn(Optional.empty());

    var snapshot = service.inspect(10L);

    assertThat(snapshot.contractRevision()).isNull();
    assertThat(snapshot.contractSha256()).isNull();
    assertThat(snapshot.domains()).hasSize(6);
    assertThat(domain(snapshot, "METRIC").status()).isEqualTo("NOT_CONFIGURED");
    assertThat(domain(snapshot, "DATADEV_RUNTIME").status()).isEqualTo("NOT_CONNECTED");
    assertThat(domain(snapshot, "QUALITY").status()).isEqualTo("NOT_CONNECTED");
    assertThat(domain(snapshot, "DATASET").status()).isEqualTo("NOT_CONNECTED");
    assertThat(domain(snapshot, "DATA_SERVICE").status()).isEqualTo("NOT_CONNECTED");
    assertThat(domain(snapshot, "CONSUMPTION_AUTHORIZATION").status()).isEqualTo("NOT_CONNECTED");
    verifyNoInteractions(metric);
  }

  @Test
  void inspectsTheExactPinnedMetricPublicationAndExposesOnlyMinimalEvidence() {
    MetricPublicationPin pin = new MetricPublicationPin(70L, 71L, 4,
        "c".repeat(64), 72L);
    Contract contract = contract(null, pin);
    when(contracts.inspect(10L)).thenReturn(Optional.of(saved(contract)));
    when(metric.inspect(any(Reference.class))).thenReturn(new Evidence(Status.VERIFIED_ACTIVE));

    var snapshot = service.inspect(10L);
    var item = domain(snapshot, "METRIC");

    assertThat(snapshot.contractRevision()).isEqualTo(3L);
    assertThat(snapshot.contractSha256()).isEqualTo("a".repeat(64));
    assertThat(item.status()).isEqualTo("VERIFIED");
    assertThat(item.sourceStatus()).isEqualTo("VERIFIED_ACTIVE");
    assertThat(item.evidenceId()).isEqualTo("72");
    assertThat(item.version()).isEqualTo("V4");
    assertThat(item.digest()).isEqualTo("c".repeat(64));
    verify(metric).inspect(new Reference(70L, 71L, 4, "c".repeat(64), 72L));
    verify(metric, never()).verifyForNewContract(any());
  }

  @Test
  void legacyIdOnlyMetricReferenceRemainsUnknownAndIsNotAutoEnriched() {
    when(contracts.inspect(10L)).thenReturn(Optional.of(saved(contract(71L, null))));

    var item = domain(service.inspect(10L), "METRIC");

    assertThat(item.status()).isEqualTo("UNKNOWN");
    assertThat(item.sourceStatus()).isEqualTo("LEGACY_INCOMPLETE");
    assertThat(item.summary()).contains("不会猜测补齐");
    verifyNoInteractions(metric);
  }

  @Test
  void providerFailureNeverBecomesVerified() {
    MetricPublicationPin pin = new MetricPublicationPin(70L, 71L, 4,
        "c".repeat(64), 72L);
    when(contracts.inspect(10L)).thenReturn(Optional.of(saved(contract(null, pin))));
    when(metric.inspect(any(Reference.class))).thenThrow(new IllegalStateException("provider down"));

    var item = domain(service.inspect(10L), "METRIC");

    assertThat(item.status()).isEqualTo("UNKNOWN");
    assertThat(item.sourceStatus()).isEqualTo("UNAVAILABLE");
    assertThat(item.summary()).contains("未将失败当作通过");
    assertThat(item.evidenceId()).isNull();
    assertThat(item.digest()).isNull();
  }

  @Test
  void forbiddenMetricReferenceDoesNotExposePublicationIdentity() {
    MetricPublicationPin pin = new MetricPublicationPin(70L, 71L, 4,
        "c".repeat(64), 72L);
    when(contracts.inspect(10L)).thenReturn(Optional.of(saved(contract(null, pin))));
    when(metric.inspect(any(Reference.class))).thenReturn(new Evidence(Status.FORBIDDEN));

    var item = domain(service.inspect(10L), "METRIC");

    assertThat(item.status()).isEqualTo("FORBIDDEN");
    assertThat(item.evidenceId()).isNull();
    assertThat(item.version()).isNull();
    assertThat(item.digest()).isNull();
  }

  @Test
  void missingOrCrossProjectMetricReferenceIsUnknownWithoutIdentityDisclosure() {
    MetricPublicationPin pin = new MetricPublicationPin(70L, 71L, 4,
        "c".repeat(64), 72L);
    when(contracts.inspect(10L)).thenReturn(Optional.of(saved(contract(null, pin))));
    when(metric.inspect(any(Reference.class))).thenReturn(new Evidence(Status.NOT_FOUND));

    var item = domain(service.inspect(10L), "METRIC");

    assertThat(item.status()).isEqualTo("UNKNOWN");
    assertThat(item.sourceStatus()).isEqualTo("NOT_FOUND");
    assertThat(item.evidenceId()).isNull();
    assertThat(item.version()).isNull();
    assertThat(item.digest()).isNull();
  }

  private static AdsApplicationEvidenceService.DomainEvidence domain(
      AdsApplicationEvidenceService.Snapshot snapshot, String code) {
    return snapshot.domains().stream().filter(item -> item.domainCode().equals(code))
        .findFirst().orElseThrow();
  }

  private static Saved saved(Contract contract) {
    return new Saved(10L, 3L, "a".repeat(64), "{}", contract, "alice", "2026-10-18T10:00:00");
  }

  private static Contract contract(Long legacyMetricVersionId, MetricPublicationPin pin) {
    return new Contract("order_overview", "订单经营日报", "alice", "运营看板",
        "REPORT", "DWS", null, List.of(), List.of(), "", "",
        legacyMetricVersionId, pin, "DAILY", "b".repeat(64));
  }
}
