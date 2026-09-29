package io.yak.ops.business.datasource.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.datasource.api.DataSourceChangedEvent;
import io.yak.ops.business.datasource.api.DataSourceChangedEvent.ChangeType;
import io.yak.ops.business.datasource.api.DataSourceReference;
import io.yak.ops.business.datasource.api.DataSourceReferenceProvider;
import io.yak.ops.business.datasource.connection.DataSourceConnectionResolver;
import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class DataSourceManagerTest {

  @Mock private DataSourceRepository repository;
  @Mock private DataSourceReader reader;
  @Mock private DataSourceConnectionResolver connectionResolver;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private BusinessAuditService auditService;
  @Mock private AuditOperationHandle auditOperation;

  @BeforeEach
  void setUpAudit() {
    org.mockito.Mockito.lenient()
        .when(auditService.start(any(AuditOperationRequest.class)))
        .thenReturn(auditOperation);
  }

  @Test
  void createBuildsAggregateFromNormalizedConnectionProfileAndAuditsLifecycle() {
    DataSourceConfigurationCommand command =
        new DataSourceConfigurationCommand(
            "orders-db",
            DataSourceDbType.MYSQL,
            DataSourceEnvironment.PROD,
            "orders",
            "{\"host\":\"127.0.0.1\"}");
    ConnectionProfile profile =
        new ConnectionProfile(
            "jdbc:mysql://127.0.0.1/orders",
            "{\"host\":\"127.0.0.1\"}",
            "{\"host\":\"127.0.0.1\"}");
    when(connectionResolver.normalize(DataSourceDbType.MYSQL, command.connectionJson()))
        .thenReturn(profile);
    // 真实仓储会把数据库主键回填到聚合，创建事件依赖这个契约。
    when(repository.insert(any(DataSourceDefinition.class)))
        .thenAnswer(
            invocation -> {
              invocation.<DataSourceDefinition>getArgument(0).assignId(42L);
              return true;
            });

    assertThat(manager().create(command)).isTrue();

    ArgumentCaptor<DataSourceDefinition> captor =
        ArgumentCaptor.forClass(DataSourceDefinition.class);
    verify(repository).insert(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("orders-db");
    assertThat(captor.getValue().getConnStatus()).isEqualTo(DataSourceConnStatus.UNKNOWN);
    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue())
        .isEqualTo(
            new DataSourceChangedEvent(
                42L, DataSourceDbType.MYSQL, "orders-db", ChangeType.CREATED));
    verify(auditOperation)
        .event(eq(AuditEventType.RESOURCE_CREATED), eq("Datasource created"), anyMap());
    verify(auditOperation).success("Datasource created");
  }

  @Test
  void updatePublishesDatasourceChangedEventAndNeverAuditsCredentialValues() {
    DataSourceConfigurationCommand command =
        new DataSourceConfigurationCommand(
            "orders-db-v2",
            DataSourceDbType.MYSQL,
            DataSourceEnvironment.TEST,
            "updated",
            "{\"host\":\"db.internal\",\"password\":\"new-super-secret\"}");
    ConnectionProfile stored =
        new ConnectionProfile(
            "jdbc:mysql://127.0.0.1/orders",
            "{\"host\":\"127.0.0.1\",\"password\":\"old-secret\"}",
            "{\"host\":\"127.0.0.1\",\"password\":\"old-secret\"}");
    ConnectionProfile merged =
        new ConnectionProfile(
            "jdbc:mysql://db.internal/orders",
            "{\"host\":\"db.internal\",\"password\":\"new-super-secret\"}",
            "{\"host\":\"db.internal\",\"password\":\"new-super-secret\"}");
    DataSourceDefinition existing =
        DataSourceDefinition.create(
            "orders-db",
            DataSourceDbType.MYSQL,
            stored,
            DataSourceEnvironment.PROD,
            null);
    // reader.require 返回的是从库里重建的聚合，主键必然已存在。
    existing.assignId(42L);
    when(reader.require(42L)).thenReturn(existing);
    when(connectionResolver.mergeStoredSecrets(existing, command.connectionJson()))
        .thenReturn(merged);
    when(repository.update(existing)).thenReturn(true);

    assertThat(manager().update(42L, command)).isTrue();

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue())
        .isEqualTo(
            new DataSourceChangedEvent(
                42L, DataSourceDbType.MYSQL, "orders-db-v2", ChangeType.UPDATED));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(auditOperation)
        .event(
            eq(AuditEventType.RESOURCE_UPDATED),
            eq("Datasource configuration updated"),
            payloadCaptor.capture());
    String payload = payloadCaptor.getValue().toString();
    assertThat(payload)
        .contains("connectionChanged=true", "credentialChanged=true")
        .doesNotContain("new-super-secret", "old-secret", "password", "jdbc:mysql");
    verify(auditOperation).success("Datasource updated");
  }

  @Test
  void failedCreateMarksAuditOperationFailedAndRethrowsBusinessError() {
    DataSourceConfigurationCommand command =
        new DataSourceConfigurationCommand(
            "orders-db",
            DataSourceDbType.MYSQL,
            DataSourceEnvironment.PROD,
            null,
            "{\"host\":\"127.0.0.1\"}");
    ConnectionProfile profile =
        new ConnectionProfile(
            "jdbc:mysql://127.0.0.1/orders",
            "{\"host\":\"127.0.0.1\"}",
            "{\"host\":\"127.0.0.1\"}");
    when(connectionResolver.normalize(DataSourceDbType.MYSQL, command.connectionJson()))
        .thenReturn(profile);
    when(repository.insert(any(DataSourceDefinition.class))).thenReturn(false);

    assertThatThrownBy(() -> manager().create(command)).isInstanceOf(RuntimeException.class);

    verify(auditOperation).failure(eq("DATASOURCE_CREATE_FAILED"), any(RuntimeException.class));
  }

  private DataSourceManager manager() {
    return manager(List.of());
  }

  private DataSourceManager manager(List<DataSourceReferenceProvider> providers) {
    return new DataSourceManager(
        repository, reader, connectionResolver, eventPublisher, auditService, providers);
  }

  private static DataSourceReferenceProvider provider(String moduleName, long count) {
    return new DataSourceReferenceProvider() {
      @Override
      public String moduleName() {
        return moduleName;
      }

      @Override
      public long countReferences(Long dataSourceId) {
        if (count < 0) throw new IllegalStateException("boom");
        return count;
      }
    };
  }

  @Test
  void deleteIsBlockedWithReferenceInventoryWhenDownstreamProvidersReportUsage() {
    DataSourceDefinition existing = mock(DataSourceDefinition.class);
    when(reader.require(42L)).thenReturn(existing);

    assertThatThrownBy(
            () -> manager(List.of(provider("离线同步", 3L), provider("数据质量", 2L))).delete(42L))
        .isInstanceOfSatisfying(
            DataSourceException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo(DataSourceErrorCode.DATASOURCE_REFERENCED);
              assertThat(exception.getUserMessage()).contains("离线同步(3 项)", "数据质量(2 项)");
            });
    verify(repository, never()).delete(any());
  }

  @Test
  void deleteProceedsWhenAllProvidersReportZeroReferences() {
    DataSourceDefinition existing = mock(DataSourceDefinition.class);
    when(existing.getId()).thenReturn(42L);
    when(existing.getName()).thenReturn("orders-db");
    when(existing.getDbType()).thenReturn(DataSourceDbType.MYSQL);
    when(existing.getEnvironment()).thenReturn(DataSourceEnvironment.PROD);
    when(reader.require(42L)).thenReturn(existing);
    when(repository.delete(42L)).thenReturn(true);

    assertThat(manager(List.of(provider("离线同步", 0L))).delete(42L)).isTrue();
    verify(repository).delete(42L);

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue())
        .isEqualTo(
            new DataSourceChangedEvent(
                42L, DataSourceDbType.MYSQL, "orders-db", ChangeType.DELETED));
  }

  @Test
  void failingReferenceProviderBlocksDeleteInsteadOfSilentlyAllowingIt() {
    DataSourceDefinition existing = mock(DataSourceDefinition.class);
    when(reader.require(42L)).thenReturn(existing);

    assertThatThrownBy(() -> manager(List.of(provider("数据质量", -1L))).delete(42L))
        .isInstanceOfSatisfying(
            DataSourceException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(DataSourceErrorCode.QUERY_FAILED));
    verify(repository, never()).delete(any());
  }

  @Test
  void referencesEndpointInventoryOnlyListsModulesWithUsage() {
    DataSourceDefinition existing = mock(DataSourceDefinition.class);
    when(existing.getId()).thenReturn(42L);
    when(reader.require(42L)).thenReturn(existing);

    assertThat(
            manager(List.of(provider("离线同步", 3L), provider("数据质量", 0L), provider("主数据", 1L)))
                .references(42L))
        .containsExactly(
            new DataSourceReference("离线同步", 3L), new DataSourceReference("主数据", 1L));
  }
}
