package io.yak.ops.business.dataservice.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.documentation.DataServiceDocumentationManager;
import io.yak.ops.business.dataservice.documentation.DataServiceDocumentationReader;
import io.yak.ops.business.dataservice.documentation.DataServiceRequestParameterContract;
import io.yak.ops.business.dataservice.documentation.DocumentationFingerprint;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ParameterDoc;
import io.yak.ops.business.dataservice.execution.DataServiceSqlCompiler;
import io.yak.ops.business.dataservice.management.DataServiceManager;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.ParameterContract;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.ResolvedSource;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.ResponseFieldContract;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.SourceContract;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.SourceDescriptor;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
import io.yak.ops.business.dataservice.repository.DataServiceDocumentationRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataServicePublisherTest {

  private static final String SOURCE_TYPE = "DATA_DEVELOPMENT_DATA_SERVICE";
  private DataServiceSourceProvider provider;
  private DataServiceReader dataServiceReader;
  private DataServiceManager manager;
  private DataServiceViewFactory viewFactory;
  private DataServiceDocumentationManager documentationManager;
  private DataServiceSourceRegistry registry;
  private DataServicePublicationReader publicationReader;
  private DataServicePublisher publisher;

  @BeforeEach
  void setUp() {
    provider = mock(DataServiceSourceProvider.class);
    when(provider.sourceType()).thenReturn(SOURCE_TYPE);
    when(provider.managesServiceDefinition()).thenReturn(true);
    registry = new DataServiceSourceRegistry(List.of(provider));
    dataServiceReader = mock(DataServiceReader.class);
    manager = mock(DataServiceManager.class);
    viewFactory = mock(DataServiceViewFactory.class);
    documentationManager = mock(DataServiceDocumentationManager.class);
    publicationReader = new DataServicePublicationReader(registry, dataServiceReader, viewFactory);
    publisher = new DataServicePublisher(
        publicationReader,
        registry,
        dataServiceReader,
        manager,
        viewFactory,
        documentationManager,
        new DataServiceSqlCompiler());
  }

  @Test
  void sourceManagedPublishUsesRevisionOwnedDefinitionAndRuntime() {
    when(provider.resolve("100")).thenReturn(resolved("ONLINE", false));
    when(dataServiceReader.findBySource(SOURCE_TYPE, "100")).thenReturn(Optional.empty());
    DataServiceDefinition saved = definition(9L, false, false);
    when(manager.savePublished(any(), any(), any(), any())).thenReturn(saved);
    DataServiceView view = mock(DataServiceView.class);
    when(viewFactory.view(saved)).thenReturn(view);

    assertThat(publisher.publish(new PublishRequest(
        SOURCE_TYPE, "100", "client-name", "/client", 9999, 99, false, "client-description")))
        .isSameAs(view);

    ArgumentCaptor<DataServiceSettings> settings = ArgumentCaptor.forClass(DataServiceSettings.class);
    ArgumentCaptor<PublishedRuntimeSnapshot> runtime =
        ArgumentCaptor.forClass(PublishedRuntimeSnapshot.class);
    ArgumentCaptor<SourceReference> source = ArgumentCaptor.forClass(SourceReference.class);
    verify(manager).savePublished(any(), settings.capture(), runtime.capture(), source.capture());
    assertThat(settings.getValue().name()).isEqualTo("订单查询 API");
    assertThat(settings.getValue().path()).isEqualTo("/orders-v2");
    assertThat(settings.getValue().maxRows()).isEqualTo(500);
    assertThat(settings.getValue().timeoutSeconds()).isEqualTo(20);
    assertThat(settings.getValue().enabled()).isFalse();
    assertThat(settings.getValue().description()).isEqualTo("Revision 定义");
    assertThat(runtime.getValue().dataSourceId()).isEqualTo(42L);
    assertThat(runtime.getValue().sql()).contains("status = :status");
    assertThat(source.getValue().sourceRevisionId()).isEqualTo(900L);
    verify(documentationManager).save(org.mockito.ArgumentMatchers.eq(9L), any());
  }

  @Test
  void paginatedSourcePublishesAndSynchronizesRuntimeParameters() {
    when(provider.resolve("100")).thenReturn(resolved("ONLINE", true));
    when(dataServiceReader.findBySource(SOURCE_TYPE, "100")).thenReturn(Optional.empty());
    DataServiceDefinition saved = definition(9L, true, true);
    when(manager.savePublished(any(), any(), any(), any())).thenReturn(saved);
    when(dataServiceReader.require(9L)).thenReturn(saved);
    DataServiceView view = mock(DataServiceView.class);
    when(viewFactory.view(saved)).thenReturn(view);

    DataServiceDocumentationRepository documentationRepository =
        mock(DataServiceDocumentationRepository.class);
    when(documentationRepository.findByApiId(9L)).thenReturn(Optional.empty());
    DataServiceSqlCompiler compiler = new DataServiceSqlCompiler();
    DataServiceRequestParameterContract requestParameterContract =
        new DataServiceRequestParameterContract(compiler);
    DocumentationFingerprint fingerprint = new DocumentationFingerprint();
    DataServiceDocumentationReader documentationReader = new DataServiceDocumentationReader(
        dataServiceReader, documentationRepository, requestParameterContract, fingerprint);
    DataServiceDocumentationManager realDocumentationManager =
        new DataServiceDocumentationManager(
            dataServiceReader,
            documentationRepository,
            documentationReader,
            requestParameterContract,
            fingerprint);
    DataServicePublisher paginatedPublisher = new DataServicePublisher(
        publicationReader,
        registry,
        dataServiceReader,
        manager,
        viewFactory,
        realDocumentationManager,
        compiler);

    assertThat(paginatedPublisher.publish(new PublishRequest(
        SOURCE_TYPE, "100", null, null, null, null, true, null)))
        .isSameAs(view);

    ArgumentCaptor<DataServiceDocumentation> documentation =
        ArgumentCaptor.forClass(DataServiceDocumentation.class);
    verify(documentationRepository).save(documentation.capture());
    assertThat(documentation.getValue().parameters()).extracting(ParameterDoc::name)
        .containsExactly("status", "returnTotalNum", "pageNum", "pageSize");
    assertThat(documentation.getValue().parameters().get(0).required()).isTrue();
    assertThat(documentation.getValue().parameters().subList(1, 4))
        .allSatisfy(parameter -> assertThat(parameter.required()).isFalse());
  }

  @Test
  void rejectsSourceThatIsNotOnlineBeforePersisting() {
    when(provider.resolve("100")).thenReturn(resolved("OFFLINE", false));

    assertThatThrownBy(() -> publisher.publish(
        new PublishRequest(SOURCE_TYPE, "100", null, null, null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ONLINE");

    verify(manager, never()).savePublished(any(), any(), any(), any());
    verify(documentationManager, never()).save(any(), any());
  }

  private ResolvedSource resolved(String status, boolean paginationEnabled) {
    SourceDescriptor descriptor = new SourceDescriptor(
        SOURCE_TYPE,
        "100",
        "订单查询 API",
        "DATA_SERVICE",
        status,
        900L,
        2,
        42L,
        500,
        20,
        "/orders-v2",
        "Revision 定义",
        Instant.parse("2026-08-16T01:00:00Z"),
        paginationEnabled);
    List<ParameterContract> parameters = paginationEnabled
        ? List.of(
            new ParameterContract("status", "STRING", true, "订单状态", "PAID"),
            new ParameterContract(
                "returnTotalNum", "BOOLEAN", false, "是否返回分页总数，默认 true", "true"),
            new ParameterContract("pageNum", "INTEGER", false, "页码，从 1 开始，默认 1", "1"),
            new ParameterContract(
                "pageSize", "INTEGER", false, "每页条数，默认 20，不超过服务最大行数", "20"))
        : List.of(new ParameterContract("status", "STRING", true, "订单状态", "PAID"));
    return new ResolvedSource(
        descriptor,
        "select id from orders where status = :status",
        new SourceContract(
            parameters,
            List.of(new ResponseFieldContract("id", "INTEGER", false, "订单 ID", "1"))));
  }

  private DataServiceDefinition definition(
      Long id, boolean enabled, boolean paginationEnabled) {
    LocalDateTime now = LocalDateTime.of(2026, 8, 24, 10, 0);
    return DataServiceDefinition.restore(
        id,
        new DataServiceSettings(
            "订单查询 API",
            "/orders-v2",
            500,
            20,
            enabled,
            "Revision 定义",
            paginationEnabled),
        new PublishedRuntimeSnapshot(42L, "select id from orders where status = :status"),
        new SourceReference(SOURCE_TYPE, "100", 900L, 2),
        RuntimePolicy.defaults(true),
        AuthMode.NONE,
        now,
        now);
  }
}
