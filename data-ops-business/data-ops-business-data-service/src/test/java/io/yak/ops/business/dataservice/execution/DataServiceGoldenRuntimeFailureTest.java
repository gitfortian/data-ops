package io.yak.ops.business.dataservice.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.access.DataServiceAuthorizer;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AccessContext;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.runtime.LocalDataServiceRuntime;
import io.yak.ops.core.project.ProjectContextScope;
import io.yak.ops.core.security.ActionAuthorization;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Golden Path B negative contract: runtime failure remains failed source evidence. */
class DataServiceGoldenRuntimeFailureTest {

  @Test
  void runtimeFailureIsAuditedWithManagedConsumerAndPropagatesToCaller() {
    DataServiceDefinition definition = definition();
    DataServiceReader reader = mock(DataServiceReader.class);
    DataServiceAuthorizer authorizer = mock(DataServiceAuthorizer.class);
    DataServiceQueryExecutor executor = mock(DataServiceQueryExecutor.class);
    DataServiceInvocationRecorder recorder = mock(DataServiceInvocationRecorder.class);
    ProjectContextScope projectContextScope = mock(ProjectContextScope.class);
    ActionAuthorization actionAuthorization = mock(ActionAuthorization.class);

    AccessContext access =
        new AccessContext("API_KEY", 5L, 21L, "Golden BI Consumer", "yak_gold");

    when(reader.requireByPath("/orders")).thenReturn(definition);
    when(authorizer.authorize(definition, "yak-key", "10.0.0.8")).thenReturn(access);
    when(projectContextScope.call(any(), any())).thenAnswer(invocation -> {
      Supplier<?> action = invocation.getArgument(1);
      return action.get();
    });
    when(executor.execute(eq(definition), any(), any()))
        .thenThrow(new IllegalStateException("source unavailable"));

    DataServiceInvoker invoker = new DataServiceInvoker(
        reader,
        authorizer,
        new DataServiceSqlCompiler(),
        executor,
        new LocalDataServiceRuntime(),
        recorder,
        projectContextScope,
        actionAuthorization);

    Map<String, String> parameters = Map.of("id", "1");

    assertThatThrownBy(() -> invoker.invoke("/orders", parameters, "yak-key", "10.0.0.8"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("source unavailable");

    ArgumentCaptor<AccessContext> accessCaptor = ArgumentCaptor.forClass(AccessContext.class);
    verify(recorder).record(
        eq(definition),
        eq(parameters),
        eq(false),
        anyLong(),
        eq(0),
        eq("source unavailable"),
        accessCaptor.capture());

    assertThat(accessCaptor.getValue().consumerId()).isEqualTo(21L);
    assertThat(accessCaptor.getValue().callerType()).isEqualTo("API_KEY");
  }

  private DataServiceDefinition definition() {
    LocalDateTime publishedAt = LocalDateTime.of(2026, 9, 26, 9, 0);
    return DataServiceDefinition.restore(
        7L,
        3L,
        11L,
        new DataServiceSettings("Orders", "/orders", 100, 30, true, null, false),
        new PublishedRuntimeSnapshot(9L, "select id from orders where id = :id"),
        new SourceReference("DATASET", "42", 101L, 4),
        new RuntimePolicy(false, 60, 100, false, 5, 30),
        AuthMode.API_KEY,
        publishedAt,
        publishedAt);
  }
}
