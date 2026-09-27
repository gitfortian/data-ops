package io.yak.ops.business.metric.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.repository.MetricPublicationRepository;
import io.yak.ops.common.bean.po.metric.MetricActivePublicationPO;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricLifecycleServiceTest {

  private MetricCatalogService catalogService;
  private MetricPublicationRepository publicationRepository;
  private MetricLifecycleService service;

  @BeforeEach
  void setUp() {
    catalogService = mock(MetricCatalogService.class);
    publicationRepository = mock(MetricPublicationRepository.class);
    service = new MetricLifecycleService(catalogService, publicationRepository);
  }

  @Test
  void activePublicationMustBeWithdrawnBeforeDisable() {
    when(publicationRepository.findActive(7L)).thenReturn(new MetricActivePublicationPO());

    assertThatThrownBy(() -> service.changeStatus(7L, "DISABLED", "alice"))
        .isInstanceOf(MetricException.class)
        .satisfies(error -> assertThat(((MetricException) error).getErrorCode())
            .isEqualTo(MetricErrorCode.ACTIVE_PUBLICATION_EXISTS));

    verify(catalogService, never()).changeStatus(7L, "DISABLED", "alice");
  }

  @Test
  void disableAfterWithdrawalContinuesThroughExistingCatalogRules() {
    Metric expected = mock(Metric.class);
    when(publicationRepository.findActive(7L)).thenReturn(null);
    when(catalogService.changeStatus(7L, "DISABLED", "alice")).thenReturn(expected);

    assertThat(service.changeStatus(7L, "DISABLED", "alice")).isSameAs(expected);
    verify(catalogService).changeStatus(7L, "DISABLED", "alice");
  }

  @Test
  void enableDoesNotRequirePublicationLookup() {
    Metric expected = mock(Metric.class);
    when(catalogService.changeStatus(7L, "ENABLED", "alice")).thenReturn(expected);

    assertThat(service.changeStatus(7L, "ENABLED", "alice")).isSameAs(expected);
    verify(publicationRepository, never()).findActive(7L);
  }

  @Test
  void anyPublicationLedgerPreventsPhysicalMetricDeletion() {
    when(publicationRepository.hasEvents(7L)).thenReturn(true);

    assertThatThrownBy(() -> service.delete(7L))
        .isInstanceOf(MetricException.class)
        .satisfies(error -> assertThat(((MetricException) error).getErrorCode())
            .isEqualTo(MetricErrorCode.PUBLICATION_HISTORY_EXISTS));

    verify(catalogService, never()).delete(7L);
  }

  @Test
  void neverPublishedMetricStillUsesExistingDeleteRules() {
    when(publicationRepository.hasEvents(7L)).thenReturn(false);

    service.delete(7L);

    verify(catalogService).delete(7L);
  }
}
