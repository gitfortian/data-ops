package io.yak.ops.business.dataservice.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.access.DataServiceAuthorizer;
import io.yak.ops.business.dataservice.access.DataServiceUnauthorizedException;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceQueryResponse;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AccessContext;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.runtime.LocalDataServiceRuntime;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataServiceInvokerTest {

  private DataServiceReader reader;
  private DataServiceAuthorizer authorizer;
  private DataServiceQueryExecutor executor;
  private DataServiceInvocationRecorder recorder;
  private ProjectContextScope projectContextScope;
  private DataServiceInvoker invoker;
  private DataServiceDefinition definition;

  @BeforeEach
  void setUp() {
    reader = mock(DataServiceReader.class);
    authorizer = mock(DataServiceAuthorizer.class);
    executor = mock(DataServiceQueryExecutor.class);
    recorder = mock(DataServiceInvocationRecorder.class);
    projectContextScope = mock(ProjectContextScope.class);
    when(projectContextScope.call(any(), any())).thenAnswer(invocation -> {
      Supplier<?> action = invocation.getArgument(1);
      return action.get();
    });
    invoker = new DataServiceInvoker(
        reader,
        authorizer,
        new DataServiceSqlCompiler(),
        executor,
        new LocalDataServiceRuntime(),
        recorder,
        projectContextScope);
    definition = definition();
    when(reader.requireByPath("/orders")).thenReturn(definition);
    when(authorizer.authorize(definition, null, null)).thenReturn(AccessContext.publicAccess());
  }

  @Test
  void publicInvocationRestoresResolvedApiProjectContext() {
    DataServiceQueryResponse response = new DataServiceQueryResponse(
        List.of("id"), List.of(Map.of("id", 1L)), false, 1, 8L);
    when(executor.execute(eq(definition), any(), isNull())).thenReturn(response);

    invoker.invoke("orders", Map.of("id", "1"), null);

    verify(projectContextScope).call(eq(new ProjectContext(3L, null)), any());
  }

  @Test
  void publicInvocationPassesResolvedClientIpIntoAccessAuthorization() {
    DataServiceQueryResponse response = new DataServiceQueryResponse(
        List.of("id"), List.of(Map.of("id", 1L)), false, 1, 8L);
    when(authorizer.authorize(definition, null, "203.0.113.8"))
        .thenReturn(AccessContext.publicAccess());
    when(executor.execute(eq(definition), any(), isNull())).thenReturn(response);

    invoker.invoke("orders", Map.of("id", "1"), null, "203.0.113.8");

    verify(authorizer).authorize(definition, null, "203.0.113.8");
  }

  @Test
  void successfulInvocationIsNotFailedByAuditStorageOutage() {
    DataServiceQueryResponse response = new DataServiceQueryResponse(
        List.of("id"),
        List.of(Map.of("id", 1L)),
        false,
        1,
        8L);
    when(executor.execute(eq(definition), any(), isNull())).thenReturn(response);
    doThrow(new IllegalStateException("audit db down"))
        .when(recorder)
        .record(eq(definition), any(), eq(true), anyLong(), eq(1), isNull(), any());

    DataServiceQueryResponse result = invoker.invoke("orders", Map.of("id", "1"), null);

    assertThat(result.rows()).containsExactly(Map.of("id", 1L));
    verify(executor).execute(eq(definition), any(), isNull());
  }

  @Test
  void invocationFailureIsNotReplacedByAuditStorageFailure() {
    IllegalStateException queryFailure = new IllegalStateException("datasource down");
    when(executor.execute(eq(definition), any(), isNull())).thenThrow(queryFailure);
    doThrow(new IllegalStateException("audit db down"))
        .when(recorder)
        .record(eq(definition), any(), eq(false), anyLong(), eq(0), eq("datasource down"), any());

    assertThatThrownBy(() -> invoker.invoke("/orders", Map.of("id", "1"), null))
        .isSameAs(queryFailure);
  }

  @Test
  void authorizationFailureKeepsItsOriginalHttpSemanticWhenAuditFails() {
    DataServiceUnauthorizedException unauthorized =
        new DataServiceUnauthorizedException("invalid api key");
    when(authorizer.authorize(definition, "bad-key", null)).thenThrow(unauthorized);
    doThrow(new IllegalStateException("audit db down"))
        .when(recorder)
        .record(eq(definition), any(), eq(false), eq(0L), eq(0), eq("invalid api key"), any());

    assertThatThrownBy(() -> invoker.invoke("/orders", Map.of("id", "1"), "bad-key"))
        .isSameAs(unauthorized);
  }

  @Test
  void runtimeNamespaceChangesWhenPersistedGenerationChanges() {
    DataServiceDefinition newer = DataServiceDefinition.restore(
        7L,
        3L,
        12L,
        definition.settings(),
        definition.runtimeSnapshot(),
        definition.sourceReference(),
        definition.runtimePolicy(),
        AuthMode.NONE,
        LocalDateTime.of(2026, 8, 28, 10, 0),
        LocalDateTime.of(2026, 8, 28, 10, 0));

    assertThat(definition.runtimeGeneration()).isEqualTo(11L);
    assertThat(invoker.runtimeNamespace(definition))
        .isNotEqualTo(invoker.runtimeNamespace(newer));
  }

  private DataServiceDefinition definition() {
    return DataServiceDefinition.restore(
        7L,
        3L,
        11L,
        new DataServiceSettings("Orders", "/orders", 100, 30, true, null, false),
        new PublishedRuntimeSnapshot(9L, "select id from orders where id = :id"),
        new SourceReference("TEST", "orders", 101L, 1),
        new RuntimePolicy(false, 60, 100, false, 5, 30),
        AuthMode.NONE,
        LocalDateTime.of(2026, 8, 28, 10, 0),
        LocalDateTime.of(2026, 8, 28, 10, 0));
  }
}
