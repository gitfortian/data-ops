package io.yak.ops.business.metric.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.repository.MetricPublicationRepository;
import io.yak.ops.business.metric.repository.MetricValidationEvidenceRepository;
import io.yak.ops.common.bean.po.metric.MetricActivePublicationPO;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricLifecycleServiceTest {

  private MetricCatalogService catalogService;
  private MetricPublicationRepository publicationRepository;
  private MetricValidationEvidenceRepository validationEvidenceRepository;
  private MetricLifecycleService service;

  @BeforeEach
  void setUp() {
    catalogService = mock(MetricCatalogService.class);
    publicationRepository = mock(MetricPublicationRepository.class);
    validationEvidenceRepository = mock(MetricValidationEvidenceRepository.class);
    when(publicationRepository.lockCurrentMetricVersion(7L)).thenReturn(1);
    service = new MetricLifecycleService(catalogService, publicationRepository, validationEvidenceRepository);
  }

  @Test
  void activePublicationMustBeWithdrawnBeforeDisable() {
    when(publicationRepository.findActive(7L)).thenReturn(new MetricActivePublicationPO());

    assertThatThrownBy(() -> service.changeStatus(7L, "DISABLED", "alice"))
        .isInstanceOf(MetricException.class)
        .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                ((MetricException) error).getErrorCode())
            .isEqualTo(MetricErrorCode.ACTIVE_PUBLICATION_EXISTS));

    verify(catalogService, never()).changeStatus(7L, "DISABLED", "alice");
  }

  @Test
  void disableAfterWithdrawalContinuesThroughExistingCatalogRules() {
    when(publicationRepository.findActive(7L)).thenReturn(null);

    service.changeStatus(7L, "DISABLED", "alice");

    verify(catalogService).changeStatus(7L, "DISABLED", "alice");
  }

  @Test
  void enableDoesNotRequirePublicationLookup() {
    service.changeStatus(7L, "ENABLED", "alice");

    verify(publicationRepository, never()).findActive(7L);
    verify(catalogService).changeStatus(7L, "ENABLED", "alice");
  }

  @Test
  void anyPublicationLedgerPreventsPhysicalMetricDeletion() {
    when(publicationRepository.hasEvents(7L)).thenReturn(true);

    assertThatThrownBy(() -> service.delete(7L))
        .isInstanceOf(MetricException.class)
        .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                ((MetricException) error).getErrorCode())
            .isEqualTo(MetricErrorCode.PUBLICATION_HISTORY_EXISTS));

    verify(catalogService, never()).delete(7L);
  }

  @Test
  void validationEvidencePreventsPhysicalMetricDeletion() {
    when(validationEvidenceRepository.hasEvidence(7L)).thenReturn(true);

    assertThatThrownBy(() -> service.delete(7L))
        .isInstanceOf(MetricException.class)
        .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                ((MetricException) error).getErrorCode())
            .isEqualTo(MetricErrorCode.GOVERNANCE_EVIDENCE_EXISTS));

    verify(catalogService, never()).delete(7L);
  }

  @Test
  void neverPublishedMetricStillUsesExistingDeleteRules() {
    when(publicationRepository.hasEvents(7L)).thenReturn(false);

    service.delete(7L);

    verify(catalogService).delete(7L);
  }
}
