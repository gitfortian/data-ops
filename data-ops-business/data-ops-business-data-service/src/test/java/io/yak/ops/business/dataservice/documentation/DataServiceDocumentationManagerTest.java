package io.yak.ops.business.dataservice.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ParameterDoc;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ResponseFieldDoc;
import io.yak.ops.business.dataservice.execution.DataServiceSqlCompiler;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceDocumentationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataServiceDocumentationManagerTest {

  private DataServiceReader dataServiceReader;
  private DataServiceDocumentationRepository repository;
  private DataServiceDocumentationReader reader;
  private DataServiceDocumentationManager manager;

  @BeforeEach
  void setUp() {
    dataServiceReader = mock(DataServiceReader.class);
    repository = mock(DataServiceDocumentationRepository.class);
    DataServiceSqlCompiler compiler = new DataServiceSqlCompiler();
    DataServiceRequestParameterContract requestParameterContract =
        new DataServiceRequestParameterContract(compiler);
    DocumentationFingerprint fingerprint = new DocumentationFingerprint();
    reader = new DataServiceDocumentationReader(
        dataServiceReader, repository, requestParameterContract, fingerprint);
    manager = new DataServiceDocumentationManager(
        dataServiceReader, repository, reader, requestParameterContract, fingerprint);
  }

  @Test
  void currentSqlParametersRemainSourceOfTruth() {
    DataServiceDefinition definition = definition(
        "select * from orders where status = :status and tenant_id = :tenantId", false);
    when(dataServiceReader.require(7L)).thenReturn(definition);
    when(repository.findByApiId(7L)).thenReturn(Optional.of(new DataServiceDocumentation(
        7L,
        new DocumentationFingerprint().sqlHash(definition.runtimeSnapshot().sql()),
        List.of(
            new ParameterDoc("status", "STRING", true, "订单状态", null),
            new ParameterDoc("removed", "STRING", true, null, null)),
        List.of(),
        null)));

    ApiDocumentation result = reader.get(7L);

    assertThat(result.parameters()).extracting(ParameterDoc::name)
        .containsExactly("status", "tenantId");
    assertThat(result.parameters().get(0).description()).isEqualTo("订单状态");
    assertThat(result.schemaStale()).isFalse();
  }

  @Test
  void paginationRuntimeParametersDoNotNeedSqlPlaceholders() {
    DataServiceDefinition definition =
        definition("select * from orders where status = :status", true);
    when(dataServiceReader.require(7L)).thenReturn(definition);
    when(repository.findByApiId(7L)).thenReturn(Optional.empty());

    ApiDocumentation result = manager.save(
        7L,
        new DocumentationInput(
            List.of(
                new ParameterDoc("status", "STRING", true, "订单状态", "PAID"),
                new ParameterDoc("returnTotalNum", "BOOLEAN", false, null, null),
                new ParameterDoc("pageNum", "INTEGER", false, null, null),
                new ParameterDoc("pageSize", "INTEGER", false, null, null)),
            List.<ResponseFieldDoc>of()));

    ArgumentCaptor<DataServiceDocumentation> saved =
        ArgumentCaptor.forClass(DataServiceDocumentation.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().parameters()).extracting(ParameterDoc::name)
        .containsExactly("status", "returnTotalNum", "pageNum", "pageSize");
    assertThat(saved.getValue().parameters().get(0).required()).isTrue();
    assertThat(saved.getValue().parameters().subList(1, 4))
        .allSatisfy(parameter -> assertThat(parameter.required()).isFalse());
    assertThat(saved.getValue().parameters().get(1).type()).isEqualTo("BOOLEAN");
    assertThat(saved.getValue().parameters().get(2).type()).isEqualTo("INTEGER");
    assertThat(saved.getValue().parameters().get(3).type()).isEqualTo("INTEGER");
    assertThat(result.parameters()).extracting(ParameterDoc::name)
        .containsExactly("status", "returnTotalNum", "pageNum", "pageSize");
  }

  @Test
  void readerRestoresOptionalPaginationParametersFromCurrentRuntimeContract() {
    DataServiceDefinition definition =
        definition("select * from orders where status = :status", true);
    when(dataServiceReader.require(7L)).thenReturn(definition);
    when(repository.findByApiId(7L)).thenReturn(Optional.of(new DataServiceDocumentation(
        7L,
        new DocumentationFingerprint().sqlHash(definition.runtimeSnapshot().sql()),
        List.of(new ParameterDoc("status", "STRING", true, "订单状态", "PAID")),
        List.of(),
        null)));

    ApiDocumentation result = reader.get(7L);

    assertThat(result.parameters()).extracting(ParameterDoc::name)
        .containsExactly("status", "returnTotalNum", "pageNum", "pageSize");
    assertThat(result.parameters().get(0).required()).isTrue();
    assertThat(result.parameters().subList(1, 4))
        .allSatisfy(parameter -> assertThat(parameter.required()).isFalse());
    assertThat(result.parameters().get(1).description()).contains("分页总数");
    assertThat(result.parameters().get(2).example()).isEqualTo("1");
    assertThat(result.parameters().get(3).example()).isEqualTo("20");
  }

  @Test
  void paginationRuntimeNamesAreReservedFromSqlParameters() {
    when(dataServiceReader.require(7L)).thenReturn(
        definition("select * from orders where id = :pageNum", true));

    assertThatThrownBy(() -> reader.get(7L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("系统分页参数名：pageNum");
  }

  @Test
  void rejectsParameterThatDoesNotExistInSqlOrRuntimeContract() {
    when(dataServiceReader.require(7L)).thenReturn(
        definition("select * from orders where status = :status", false));

    assertThatThrownBy(() -> manager.save(
        7L,
        new DocumentationInput(
            List.of(new ParameterDoc("tenantId", "STRING", true, null, null)),
            List.<ResponseFieldDoc>of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SQL 中不存在参数");

    verify(repository, never()).save(any());
  }

  private DataServiceDefinition definition(String sql, boolean paginationEnabled) {
    LocalDateTime now = LocalDateTime.of(2026, 8, 24, 10, 0);
    return DataServiceDefinition.restore(
        7L,
        new DataServiceSettings(
            "订单查询", "/orders", 1000, 30, true, "供订单系统查询", paginationEnabled),
        new PublishedRuntimeSnapshot(42L, sql),
        new SourceReference("DATA_DEVELOPMENT_RELEASE", "88", 102L, 2),
        RuntimePolicy.defaults(false),
        AuthMode.API_KEY,
        now,
        now);
  }
}
