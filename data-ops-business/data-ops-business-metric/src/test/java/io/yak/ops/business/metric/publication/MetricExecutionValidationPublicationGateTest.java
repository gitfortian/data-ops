package io.yak.ops.business.metric.publication;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.metric.publication.MetricPublicationGate.GateStatus;
import io.yak.ops.business.metric.publication.MetricPublicationGate.PublicationSubject;
import org.junit.jupiter.api.Test;

class MetricExecutionValidationPublicationGateTest {

  @Test
  void reportsMissingStandaloneMetricRuntimeAsNotApplicable() {
    MetricExecutionValidationPublicationGate gate = new MetricExecutionValidationPublicationGate();

    var evidence = gate.evaluate(new PublicationSubject(7L, 31L, 3, "sha-3"));

    assertThat(evidence.status()).isEqualTo(GateStatus.NOT_APPLICABLE);
    assertThat(evidence.provider()).isEqualTo(MetricExecutionValidationPublicationGate.PROVIDER);
    assertThat(evidence.evidenceRef()).isNull();
    assertThat(evidence.issues()).containsExactly(MetricExecutionValidationPublicationGate.REASON);
  }
}
