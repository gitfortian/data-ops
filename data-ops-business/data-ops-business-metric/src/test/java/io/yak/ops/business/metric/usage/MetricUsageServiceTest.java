package io.yak.ops.business.metric.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metric.api.MetricUsageApi;
import io.yak.ops.business.metric.domain.MetricUsage;
import io.yak.ops.business.metric.publication.MetricPublicationService;
import io.yak.ops.business.metric.publication.MetricPublicationService.PublishedMetricContract;
import io.yak.ops.business.metric.repository.MetricUsageRepository;
import io.yak.ops.business.metric.repository.MetricUsageRepository.UsageTypeCount;
import io.yak.ops.core.project.CurrentProject;
import java.sql.Connection;
import java.sql.Savepoint;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Usage SPI behavior for binding synchronization and reference summaries. */
class MetricUsageServiceTest {

  private MetricUsageRepository repository;
  private CurrentProject currentProject;
  private MetricPublicationService publicationService;
  private MetricUsageService service;

  @BeforeEach
  void setUp() {
    repository = mock(MetricUsageRepository.class);
    currentProject = mock(CurrentProject.class);
    publicationService = mock(MetricPublicationService.class);
    when(currentProject.requireProjectId()).thenReturn(7L);
    service = new MetricUsageService(repository, currentProject, publicationService);
  }

  @Test
  void syncBindingsRevokesOldRowsThenInsertsOnePerMetric() {
    when(publicationService.activeForBinding(101L)).thenReturn(published(101L, 4));
    when(publicationService.activeForBinding(102L)).thenReturn(published(102L, 2));

    service.syncBindings("DATASET", 42L, "销售明细集", List.of(101L, 102L));

    verify(repository).deleteForConsumer("DATASET", 42L);
    ArgumentCaptor<MetricUsage> inserted = ArgumentCaptor.forClass(MetricUsage.class);
    verify(repository, times(2)).append(eq(7L), inserted.capture());
    assertThat(inserted.getAllValues())
        .allSatisfy(usage -> {
          assertThat(usage.usageType()).isEqualTo("DATASET");
          assertThat(usage.usageId()).isEqualTo(42L);
          assertThat(usage.usageName()).isEqualTo("销售明细集");
        })
        .extracting(MetricUsage::metricId).containsExactly(101L, 102L);
    assertThat(inserted.getAllValues()).extracting(MetricUsage::metricVersion).containsExactly(4, 2);
  }

  @Test
  void recordStoresTheExactActivePublishedVersion() {
    when(publicationService.activeForBinding(101L)).thenReturn(published(101L, 4));
    service.record(new MetricUsageApi.MetricUsageEvent(101L, "REPORT", 42L, "订单报表", 7L, "alice"));

    ArgumentCaptor<MetricUsage> inserted = ArgumentCaptor.forClass(MetricUsage.class);
    verify(repository).append(eq(7L), inserted.capture());
    assertThat(inserted.getValue().metricVersion()).isEqualTo(4);
  }

  @Test
  void syncBindingsWithEmptyIdsClearsBindingsOnly() {
    service.syncBindings("DATASET", 42L, "销售明细集", List.of());
    verify(repository).deleteForConsumer("DATASET", 42L);
    verify(repository, never()).append(any(), any());
  }

  @Test
  void legacySyncBindingsLeavesExistingReferencesWhenMetricIsNotPublished() {
    service.syncBindings("DATASET", 42L, "销售明细集", List.of(101L));

    verify(repository, never()).deleteForConsumer("DATASET", 42L);
    verify(repository, never()).append(any(), any());
  }

  @Test
  void syncBindingsIsFailOpen() {
    when(publicationService.activeForBinding(101L)).thenReturn(published(101L, 4));
    doThrow(new RuntimeException("db down"))
        .when(repository).deleteForConsumer("DATASET", 42L);
    service.syncBindings("DATASET", 42L, "x", List.of(101L));
    verify(repository, never()).append(any(), any());
  }

  @Test
  void syncBindingsPreflightFailureDoesNotTouchTransactionOrBindings() {
    when(publicationService.activeForBinding(101L))
        .thenThrow(new IllegalStateException("published metric lookup unavailable"));
    TransactionStatus transaction = mock(TransactionStatus.class);
    boolean previouslyActive = TransactionSynchronizationManager.isActualTransactionActive();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try (MockedStatic<TransactionAspectSupport> transactionContext =
        mockStatic(TransactionAspectSupport.class)) {
      transactionContext.when(TransactionAspectSupport::currentTransactionStatus)
          .thenReturn(transaction);
      service.syncBindings("DATASET", 42L, "sales", List.of(101L));
      verify(repository, never()).deleteForConsumer("DATASET", 42L);
      verify(repository, never()).append(any(), any());
      verify(transaction, never()).createSavepoint();
      verify(transaction, never()).setRollbackOnly();
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(previouslyActive);
    }
  }

  @Test
  void syncBindingsRollsBackOnlyTheReferenceReplacementWhenAnInsertFails() {
    when(publicationService.activeForBinding(101L)).thenReturn(published(101L, 4));
    when(publicationService.activeForBinding(102L)).thenReturn(published(102L, 2));
    doThrow(new RuntimeException("second insert failed"))
        .when(repository).append(eq(7L),
            org.mockito.ArgumentMatchers.argThat(value -> value.metricId().equals(102L)));
    TransactionStatus transaction = mock(TransactionStatus.class);
    Object savepoint = new Object();
    when(transaction.createSavepoint()).thenReturn(savepoint);
    boolean previouslyActive = TransactionSynchronizationManager.isActualTransactionActive();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try (MockedStatic<TransactionAspectSupport> transactionContext =
        mockStatic(TransactionAspectSupport.class)) {
      transactionContext.when(TransactionAspectSupport::currentTransactionStatus)
          .thenReturn(transaction);
      service.syncBindings("DATASET", 42L, "sales", List.of(101L, 102L));
      verify(repository).deleteForConsumer("DATASET", 42L);
      verify(repository, times(2)).append(eq(7L), any());
      verify(transaction).rollbackToSavepoint(savepoint);
      verify(transaction).releaseSavepoint(savepoint);
      verify(transaction, never()).setRollbackOnly();
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(previouslyActive);
    }
  }

  @Test
  void syncBindingsSuccessfulReplacementReleasesSavepointWithoutRollingBack() {
    when(publicationService.activeForBinding(101L)).thenReturn(published(101L, 4));
    TransactionStatus transaction = mock(TransactionStatus.class);
    Object savepoint = new Object();
    when(transaction.createSavepoint()).thenReturn(savepoint);
    boolean previouslyActive = TransactionSynchronizationManager.isActualTransactionActive();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try (MockedStatic<TransactionAspectSupport> transactionContext =
        mockStatic(TransactionAspectSupport.class)) {
      transactionContext.when(TransactionAspectSupport::currentTransactionStatus)
          .thenReturn(transaction);
      service.syncBindings("DATASET", 42L, "sales", List.of(101L));
      verify(repository).deleteForConsumer("DATASET", 42L);
      verify(repository).append(eq(7L), any());
      verify(transaction).releaseSavepoint(savepoint);
      verify(transaction, never()).rollbackToSavepoint(savepoint);
      verify(transaction, never()).setRollbackOnly();
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(previouslyActive);
    }
  }

  @Test
  void syncBindingsCannotStartReplacementWithoutSavepointInActiveTransaction() {
    when(publicationService.activeForBinding(101L)).thenReturn(published(101L, 4));
    TransactionStatus transaction = mock(TransactionStatus.class);
    when(transaction.createSavepoint()).thenThrow(new IllegalStateException("savepoints disabled"));
    boolean previouslyActive = TransactionSynchronizationManager.isActualTransactionActive();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try (MockedStatic<TransactionAspectSupport> transactionContext =
        mockStatic(TransactionAspectSupport.class)) {
      transactionContext.when(TransactionAspectSupport::currentTransactionStatus)
          .thenReturn(transaction);
      service.syncBindings("DATASET", 42L, "sales", List.of(101L));
      verify(repository, never()).deleteForConsumer("DATASET", 42L);
      verify(repository, never()).append(any(), any());
      verify(transaction, never()).setRollbackOnly();
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(previouslyActive);
    }
  }

  @Test
  void syncBindingsFailsClosedIfSavepointRollbackItselfFails() {
    when(publicationService.activeForBinding(101L)).thenReturn(published(101L, 4));
    doThrow(new IllegalStateException("insert failed")).when(repository).append(eq(7L), any());
    TransactionStatus transaction = mock(TransactionStatus.class);
    Object savepoint = new Object();
    when(transaction.createSavepoint()).thenReturn(savepoint);
    doThrow(new IllegalStateException("connection lost"))
        .when(transaction).rollbackToSavepoint(savepoint);
    boolean previouslyActive = TransactionSynchronizationManager.isActualTransactionActive();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try (MockedStatic<TransactionAspectSupport> transactionContext =
        mockStatic(TransactionAspectSupport.class)) {
      transactionContext.when(TransactionAspectSupport::currentTransactionStatus)
          .thenReturn(transaction);
      service.syncBindings("DATASET", 42L, "sales", List.of(101L));
      verify(transaction).setRollbackOnly();
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(previouslyActive);
    }
  }

  @Test
  void jdbcSavepointRollbackAllowsTheOuterTransactionToCommit() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Savepoint savepoint = mock(Savepoint.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getAutoCommit()).thenReturn(true);
    when(connection.setSavepoint(anyString())).thenReturn(savepoint);
    DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);

    when(publicationService.activeForBinding(101L)).thenReturn(published(101L, 4));
    doThrow(new IllegalStateException("write failed")).when(repository).append(eq(7L), any());
    new TransactionTemplate(transactionManager).execute(status -> {
      try (MockedStatic<TransactionAspectSupport> transactionContext =
          mockStatic(TransactionAspectSupport.class)) {
        transactionContext.when(TransactionAspectSupport::currentTransactionStatus)
            .thenReturn(status);
        service.syncBindings("DATASET", 42L, "sales", List.of(101L));
      }
      assertThat(status.isRollbackOnly()).isFalse();
      return null;
    });

    verify(connection).rollback(savepoint);
    verify(connection).commit();
    verify(connection, never()).rollback();
  }

  @Test
  void boundMetricIdsReturnsDistinctMetricIds() {
    when(repository.listForConsumer("DATASET", 42L)).thenReturn(List.of(
        usageRow(101L, 4), usageRow(101L, 4), usageRow(102L, 2)));
    assertThat(service.boundMetricIds("DATASET", 42L)).containsExactly(101L, 102L);
  }

  @Test
  void boundMetricVersionRefsPreservesLegacyUnknownVersions() {
    when(repository.listForConsumer("DATASET", 42L)).thenReturn(List.of(
        usageRow(101L, 4), usageRow(102L, null)));
    assertThat(service.boundMetricVersionRefs("DATASET", 42L))
        .containsExactly(new MetricUsageApi.MetricVersionRef(101L, 4),
            new MetricUsageApi.MetricVersionRef(102L, null));
  }

  @Test
  void governedBindingsRequireTheExactActivePublishedVersion() {
    when(publicationService.activeForBinding(101L)).thenReturn(published(101L, 4));

    service.syncPublishedBindings(
        "DATASET", 42L, "销售明细集", List.of(new MetricUsageApi.MetricVersionRef(101L, 4)));

    ArgumentCaptor<MetricUsage> inserted = ArgumentCaptor.forClass(MetricUsage.class);
    verify(repository).append(eq(7L), inserted.capture());
    assertThat(inserted.getValue().metricId()).isEqualTo(101L);
    assertThat(inserted.getValue().metricVersion()).isEqualTo(4);
  }

  @Test
  void governedBindingRejectsUnpublishedOrStaleVersionWithoutReplacingExistingBindings() {
    when(publicationService.activeForBinding(101L)).thenReturn(published(101L, 4));

    assertThatThrownBy(() -> service.syncPublishedBindings(
        "DATASET", 42L, "销售明细集", List.of(new MetricUsageApi.MetricVersionRef(101L, 3))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not the active Published Metric Contract");

    verify(repository, never()).deleteForConsumer("DATASET", 42L);
    verify(repository, never()).append(any(), any());
  }

  @Test
  void summaryCountsDatasetTypeSeparately() {
    when(repository.countByMetric(101L)).thenReturn(3L);
    when(repository.countGroupByType(101L)).thenReturn(List.of(
        new UsageTypeCount("DATASET", 2L), new UsageTypeCount("REPORT", 1L)));

    MetricUsageApi.UsageSummary summary = service.summary(101L);
    assertThat(summary.totalCount()).isEqualTo(3);
    assertThat(summary.datasetCount()).isEqualTo(2);
    assertThat(summary.reportCount()).isEqualTo(1);
    assertThat(summary.dashboardCount()).isZero();
  }

  private static MetricUsage usageRow(Long metricId, Integer metricVersion) {
    return new MetricUsage(metricId, metricId, metricVersion, "DATASET", 42L, null, null);
  }

  private static PublishedMetricContract published(Long metricId, int version) {
    return new PublishedMetricContract(1L, metricId, version + 100L, version,
        "digest", "{}", List.of(), "alice", null);
  }
}
