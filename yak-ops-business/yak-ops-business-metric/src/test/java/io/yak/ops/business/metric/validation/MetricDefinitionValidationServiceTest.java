package io.yak.ops.business.metric.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.repository.MetricValidationEvidenceRepository;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.business.metric.validation.MetricDefinitionValidationService.ValidationResult;
import io.yak.ops.common.bean.po.metric.MetricValidationEvidencePO;
import io.yak.ops.common.bean.po.metric.MetricVersionPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MetricDefinitionValidationServiceTest {

  private MetricVersionRepository versionRepository;
  private MetricValidationEvidenceRepository evidenceRepository;
  private MetricDefinitionValidationService service;

  @BeforeEach
  void setUp() {
    versionRepository = mock(MetricVersionRepository.class);
    evidenceRepository = mock(MetricValidationEvidenceRepository.class);
    service = new MetricDefinitionValidationService(versionRepository, evidenceRepository);
    when(evidenceRepository.append(any())).thenAnswer(invocation -> {
      MetricValidationEvidencePO po = invocation.getArgument(0);
      po.setId(9001L);
      return po;
    });
  }

  @Test
  void validationBindsEvidenceToImmutableMetricVersion() {
    MetricVersionPO version = version(
        101L,
        7,
        "{\"metricCode\":\"GMV\",\"metricName\":\"成交金额\",\"metricType\":\"ATOMIC\","
            + "\"domainId\":1,\"processId\":2,\"caliberId\":3,\"unitId\":4,\"modelId\":5,"
            + "\"owner\":\"alice\",\"businessDesc\":\"支付成功订单成交金额\","
            + "\"measureExpr\":\"sum(pay_amount)\"}");
    when(versionRepository.findByMetricAndVersion(42L, 7)).thenReturn(version);

    var evidence = service.validate(42L, 7, "alice");

    assertThat(evidence.result()).isEqualTo(ValidationResult.READY);
    assertThat(evidence.metricId()).isEqualTo(42L);
    assertThat(evidence.metricVersionId()).isEqualTo(101L);
    assertThat(evidence.metricVersion()).isEqualTo(7);
    assertThat(evidence.provider()).isEqualTo(MetricDefinitionValidationService.PROVIDER);
    assertThat(evidence.snapshotDigest()).hasSize(64);
    assertThat(evidence.issues()).isEmpty();

    ArgumentCaptor<MetricValidationEvidencePO> captor =
        ArgumentCaptor.forClass(MetricValidationEvidencePO.class);
    verify(evidenceRepository).append(captor.capture());
    assertThat(captor.getValue().getMetricVersionId()).isEqualTo(101L);
    assertThat(captor.getValue().getMetricVersion()).isEqualTo(7);
    assertThat(captor.getValue().getResult()).isEqualTo("READY");
    assertThat(captor.getValue().getCheckedBy()).isEqualTo("alice");
  }

  @Test
  void semanticCompletenessFailuresProduceBlockedEvidence() {
    MetricVersionPO version = version(
        102L,
        3,
        "{\"metricCode\":\"UV\",\"metricName\":\"访客数\",\"metricType\":\"ATOMIC\","
            + "\"domainId\":0,\"processId\":0,\"caliberId\":0,\"unitId\":0,\"modelId\":0,"
            + "\"owner\":\"\",\"businessDesc\":\"\",\"measureExpr\":\"\"}");
    when(versionRepository.findByMetricAndVersion(8L, 3)).thenReturn(version);

    var evidence = service.validate(8L, 3, "bob");

    assertThat(evidence.result()).isEqualTo(ValidationResult.BLOCKED);
    assertThat(evidence.issues())
        .extracting(MetricDefinitionValidationService.ValidationIssue::code)
        .contains(
            "DOMAIN_REQUIRED",
            "OWNER_REQUIRED",
            "BUSINESS_DESC_REQUIRED",
            "CALIBER_REQUIRED",
            "UNIT_REQUIRED",
            "PROCESS_REQUIRED",
            "MODEL_REQUIRED",
            "MEASURE_EXPR_REQUIRED");
  }

  @Test
  void compositeDoesNotPretendReadyWithoutImmutableCompositionEvidence() {
    MetricVersionPO version = version(
        103L,
        2,
        "{\"metricCode\":\"ARPU\",\"metricName\":\"ARPU\",\"metricType\":\"COMPOSITE\","
            + "\"domainId\":1,\"caliberId\":2,\"unitId\":3,\"owner\":\"alice\","
            + "\"businessDesc\":\"客单复合指标\"}");
    when(versionRepository.findByMetricAndVersion(9L, 2)).thenReturn(version);

    var evidence = service.validate(9L, 2, "alice");

    assertThat(evidence.result()).isEqualTo(ValidationResult.BLOCKED);
    assertThat(evidence.issues())
        .extracting(MetricDefinitionValidationService.ValidationIssue::code)
        .contains("COMPOSITION_VERSION_EVIDENCE_REQUIRED");
  }

  @Test
  void invalidSnapshotIsRecordedAsBlockedInsteadOfThrowingAwayEvidence() {
    MetricVersionPO version = version(104L, 4, "not-json");
    when(versionRepository.findByMetricAndVersion(10L, 4)).thenReturn(version);

    var evidence = service.validate(10L, 4, "alice");

    assertThat(evidence.result()).isEqualTo(ValidationResult.BLOCKED);
    assertThat(evidence.issues())
        .extracting(MetricDefinitionValidationService.ValidationIssue::code)
        .containsExactly("SNAPSHOT_INVALID");
    verify(evidenceRepository).append(any(MetricValidationEvidencePO.class));
  }

  @Test
  void missingVersionCannotProduceEvidence() {
    when(versionRepository.findByMetricAndVersion(404L, 1)).thenReturn(null);

    assertThatThrownBy(() -> service.validate(404L, 1, "alice"))
        .isInstanceOf(MetricException.class)
        .hasMessageContaining("版本 v1 不存在");
  }

  private static MetricVersionPO version(Long versionId, int version, String snapshot) {
    MetricVersionPO po = new MetricVersionPO();
    po.setId(versionId);
    po.setMetricId(42L);
    po.setVersion(version);
    po.setSnapshot(snapshot);
    return po;
  }
}
