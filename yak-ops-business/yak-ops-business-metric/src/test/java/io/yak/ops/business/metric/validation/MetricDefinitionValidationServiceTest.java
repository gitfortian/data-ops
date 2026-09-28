package io.yak.ops.business.metric.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.catalog.MetricReferenceResolver;
import io.yak.ops.business.metric.catalog.MetricReferenceResolver.Reference;
import io.yak.ops.business.metric.catalog.MetricReferenceResolver.ReferenceResolution;
import io.yak.ops.business.metric.repository.MetricPublicationRepository;
import io.yak.ops.business.metric.repository.MetricValidationEvidenceRepository;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.business.metric.domain.MetricValidationEvidence;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.ValidationIssue;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.ValidationResult;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.ProviderState;
import io.yak.ops.common.bean.po.metric.MetricVersionPO;
import io.yak.ops.business.semantic.api.StandardKind;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MetricDefinitionValidationServiceTest {

  private MetricVersionRepository versionRepository;
  private MetricValidationEvidenceRepository evidenceRepository;
  private BusinessAuditService auditService;
  private AuditOperationHandle auditHandle;
  private MetricReferenceResolver referenceResolver;
  private MetricPublicationRepository metricLockRepository;
  private MetricDefinitionValidationService service;

  @BeforeEach
  void setUp() {
    versionRepository = mock(MetricVersionRepository.class);
    evidenceRepository = mock(MetricValidationEvidenceRepository.class);
    auditService = mock(BusinessAuditService.class);
    auditHandle = mock(AuditOperationHandle.class);
    referenceResolver = mock(MetricReferenceResolver.class);
    metricLockRepository = mock(MetricPublicationRepository.class);
    when(auditService.start(any())).thenReturn(auditHandle);
    when(metricLockRepository.lockCurrentMetricVersion(any())).thenReturn(1);
    when(referenceResolver.domainResolution(any())).thenReturn(ReferenceResolution.ready(Reference.EMPTY));
    when(referenceResolver.standardReferenceResolution(any(), any(StandardKind.class)))
        .thenReturn(ReferenceResolution.ready(Reference.EMPTY));
    when(referenceResolver.processResolution(any(), any()))
        .thenReturn(ReferenceResolution.ready(Reference.EMPTY));
    when(referenceResolver.modelReferenceResolution(any()))
        .thenReturn(ReferenceResolution.ready(Reference.EMPTY));
    when(referenceResolver.metricsById(any())).thenReturn(Map.of());
    service = new MetricDefinitionValidationService(
        versionRepository, evidenceRepository, auditService, referenceResolver, metricLockRepository);
    when(evidenceRepository.append(any())).thenAnswer(invocation -> {
      MetricValidationEvidence evidence = invocation.getArgument(0);
      return new MetricValidationEvidence(9001L, evidence.metricId(), evidence.metricVersionId(),
          evidence.metricVersion(), evidence.result(), evidence.providerState(), evidence.issues(),
          evidence.provider(), evidence.snapshotDigest(), evidence.checkedBy(), evidence.checkedAt());
    });
  }

  @Test
  void validationBindsEvidenceToImmutableMetricVersionAndAuditsReadyOutcome() {
    MetricVersionPO version = version(
        101L,
        7,
        "{\"metricCode\":\"GMV\",\"metricName\":\"成交金额\",\"metricType\":\"ATOMIC\","
            + "\"domainId\":1,\"processId\":2,\"caliberId\":3,\"unitId\":4,\"modelId\":5,"
            + "\"owner\":\"alice\",\"businessDesc\":\"支付成功订单成交金额\","
            + "\"measureExpr\":\"sum(pay_amount)\"}");
    when(versionRepository.findByMetricAndVersion(42L, 7)).thenReturn(version);

    var evidence = service.validate(42L, 7, "alice");

    assertThat(evidence.result()).isEqualTo(ValidationResult.PASSED);
    assertThat(evidence.providerState()).isEqualTo(ProviderState.READY);
    assertThat(evidence.metricId()).isEqualTo(42L);
    assertThat(evidence.metricVersionId()).isEqualTo(101L);
    assertThat(evidence.metricVersion()).isEqualTo(7);
    assertThat(evidence.provider()).isEqualTo(MetricDefinitionValidationService.PROVIDER);
    assertThat(evidence.snapshotDigest()).hasSize(64);
    assertThat(evidence.issues()).isEmpty();

    ArgumentCaptor<MetricValidationEvidence> captor = ArgumentCaptor.forClass(MetricValidationEvidence.class);
    verify(evidenceRepository).append(captor.capture());
    assertThat(captor.getValue().metricVersionId()).isEqualTo(101L);
    assertThat(captor.getValue().metricVersion()).isEqualTo(7);
    assertThat(captor.getValue().result()).isEqualTo(ValidationResult.PASSED);
    assertThat(captor.getValue().providerState()).isEqualTo(ProviderState.READY);
    assertThat(captor.getValue().checkedBy()).isEqualTo("alice");
    verify(auditService).start(any());
    verify(auditHandle).success("Metric definition validation PASSED");
  }

  @Test
  void semanticCompletenessFailuresProduceBlockedEvidenceAndAuditedOutcome() {
    MetricVersionPO version = version(
        102L,
        3,
        "{\"metricCode\":\"UV\",\"metricName\":\"访客数\",\"metricType\":\"ATOMIC\","
            + "\"domainId\":0,\"processId\":0,\"caliberId\":0,\"unitId\":0,\"modelId\":0,"
            + "\"owner\":\"\",\"businessDesc\":\"\",\"measureExpr\":\"\"}");
    when(versionRepository.findByMetricAndVersion(8L, 3)).thenReturn(version);

    var evidence = service.validate(8L, 3, "bob");

    assertThat(evidence.result()).isEqualTo(ValidationResult.FAILED);
    assertThat(evidence.issues())
        .extracting(ValidationIssue::code)
        .contains(
            "DOMAIN_REQUIRED",
            "OWNER_REQUIRED",
            "BUSINESS_DESC_REQUIRED",
            "CALIBER_REQUIRED",
            "UNIT_REQUIRED",
            "PROCESS_REQUIRED",
            "MODEL_REQUIRED",
            "MEASURE_EXPR_REQUIRED");
    verify(auditHandle).success("Metric definition validation FAILED");
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

    assertThat(evidence.result()).isEqualTo(ValidationResult.FAILED);
    assertThat(evidence.issues())
        .extracting(ValidationIssue::code)
        .contains("COMPOSITION_REQUIRED");
  }

  @Test
  void unavailableOwningProviderIsNotApplicableRatherThanPassedOrFailed() {
    MetricVersionPO version = version(105L, 5, validAtomicSnapshot());
    when(versionRepository.findByMetricAndVersion(42L, 5)).thenReturn(version);
    when(referenceResolver.domainResolution(1L)).thenReturn(ReferenceResolution.unavailable());

    var evidence = service.validate(42L, 5, "alice");

    assertThat(evidence.result()).isEqualTo(ValidationResult.NOT_APPLICABLE);
    assertThat(evidence.providerState()).isEqualTo(ProviderState.UNAVAILABLE);
    assertThat(evidence.issues()).extracting(ValidationIssue::severity)
        .containsExactly(io.yak.ops.business.metric.domain.MetricValidationEvidence.Severity.WARNING);
  }

  @Test
  void knownDefinitionFailureRemainsFailedWhenAnotherReferenceProviderIsUnavailable() {
    String incomplete = validAtomicSnapshot().replace("\"owner\":\"alice\"", "\"owner\":\"\"");
    MetricVersionPO version = version(115L, 5, incomplete);
    when(versionRepository.findByMetricAndVersion(42L, 5)).thenReturn(version);
    when(referenceResolver.domainResolution(1L)).thenReturn(ReferenceResolution.unavailable());

    var evidence = service.validate(42L, 5, "alice");

    assertThat(evidence.result()).isEqualTo(ValidationResult.FAILED);
    assertThat(evidence.providerState()).isEqualTo(ProviderState.UNAVAILABLE);
    assertThat(evidence.issues()).extracting(ValidationIssue::code).contains("OWNER_REQUIRED");
  }

  @Test
  void confirmedRemovedReferenceFailsValidation() {
    MetricVersionPO version = version(106L, 6, validAtomicSnapshot());
    when(versionRepository.findByMetricAndVersion(42L, 6)).thenReturn(version);
    when(referenceResolver.domainResolution(1L)).thenReturn(ReferenceResolution.removed());

    var evidence = service.validate(42L, 6, "alice");

    assertThat(evidence.result()).isEqualTo(ValidationResult.FAILED);
    assertThat(evidence.providerState()).isEqualTo(ProviderState.READY);
    assertThat(evidence.issues()).extracting(ValidationIssue::code).contains("DOMAIN_REMOVED");
  }

  @Test
  void compositeCycleAcrossExactMetricVersionsFailsValidation() {
    MetricVersionPO current = version(107L, 2,
        "{\"metricCode\":\"A\",\"metricName\":\"A\",\"metricType\":\"COMPOSITE\","
            + "\"domainId\":1,\"caliberId\":2,\"unitId\":3,\"owner\":\"alice\","
            + "\"businessDesc\":\"composite\",\"compositions\":[{\"operator\":\"REF\","
            + "\"subMetricId\":55,\"subMetricVersion\":1}]}" );
    MetricVersionPO child = version(108L, 1,
        "{\"metricCode\":\"B\",\"metricName\":\"B\",\"metricType\":\"COMPOSITE\","
            + "\"domainId\":1,\"caliberId\":2,\"unitId\":3,\"owner\":\"alice\","
            + "\"businessDesc\":\"composite\",\"compositions\":[{\"operator\":\"REF\","
            + "\"subMetricId\":9,\"subMetricVersion\":2}]}" );
    when(versionRepository.findByMetricAndVersion(9L, 2)).thenReturn(current);
    when(versionRepository.findByMetricAndVersion(55L, 1)).thenReturn(child);

    var evidence = service.validate(9L, 2, "alice");

    assertThat(evidence.result()).isEqualTo(ValidationResult.FAILED);
    assertThat(evidence.issues()).extracting(ValidationIssue::code).contains("COMPOSITION_CYCLE");
  }

  @Test
  void invalidSnapshotIsRecordedAsBlockedInsteadOfThrowingAwayEvidence() {
    MetricVersionPO version = version(104L, 4, "not-json");
    when(versionRepository.findByMetricAndVersion(10L, 4)).thenReturn(version);

    var evidence = service.validate(10L, 4, "alice");

    assertThat(evidence.result()).isEqualTo(ValidationResult.FAILED);
    assertThat(evidence.issues())
        .extracting(ValidationIssue::code)
        .containsExactly("SNAPSHOT_INVALID");
    verify(evidenceRepository).append(any(MetricValidationEvidence.class));
  }

  @Test
  void missingVersionCannotProduceEvidenceAndAuditsFailure() {
    when(versionRepository.findByMetricAndVersion(404L, 1)).thenReturn(null);

    assertThatThrownBy(() -> service.validate(404L, 1, "alice"))
        .isInstanceOf(MetricException.class)
        .hasMessageContaining("版本 v1 不存在");

    verify(auditHandle).failure(eq("METRIC_DEFINITION_VALIDATION_FAILED"), any(RuntimeException.class));
  }

  private static MetricVersionPO version(Long versionId, int version, String snapshot) {
    MetricVersionPO po = new MetricVersionPO();
    po.setId(versionId);
    po.setMetricId(42L);
    po.setVersion(version);
    po.setSnapshot(snapshot);
    return po;
  }

  private static String validAtomicSnapshot() {
    return "{\"metricCode\":\"GMV\",\"metricName\":\"成交金额\",\"metricType\":\"ATOMIC\","
        + "\"domainId\":1,\"processId\":2,\"caliberId\":3,\"unitId\":4,\"modelId\":5,"
        + "\"owner\":\"alice\",\"businessDesc\":\"支付成功订单成交金额\","
        + "\"measureExpr\":\"sum(pay_amount)\"}";
  }
}
