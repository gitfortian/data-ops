package io.yak.ops.business.dataservice.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.access.ApiKeySecretGenerator;
import io.yak.ops.business.dataservice.access.DataServiceAuthorizer;
import io.yak.ops.business.dataservice.access.DataServiceConsumerIpAccessAuthorizer;
import io.yak.ops.business.dataservice.access.DataServiceForbiddenException;
import io.yak.ops.business.dataservice.access.DataServiceIpAccessAuthorizer;
import io.yak.ops.business.dataservice.access.DataServiceRateLimiter;
import io.yak.ops.business.dataservice.access.DataServiceUnauthorizedException;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceQueryResponse;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AccessContext;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.domain.access.ConsumerAccessScope;
import io.yak.ops.business.dataservice.domain.access.DataServiceApiKey;
import io.yak.ops.business.dataservice.domain.access.DataServiceConsumer;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceApiKeyRepository;
import io.yak.ops.business.dataservice.repository.DataServiceConsumerRepository;
import io.yak.ops.business.dataservice.runtime.LocalDataServiceRuntime;
import io.yak.ops.core.project.ProjectContextScope;
import io.yak.ops.core.security.ActionAuthorization;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Golden Path B contract for the public Data Service invoke authorization boundary. */
class DataServiceGoldenPublicInvokeAccessTest {

  @Test
  void publicInvokeStillRequiresRealConsumerApiKeyAndKeepsStableConsumerIdentity() {
    DataServiceDefinition definition = definition();
    DataServiceReader reader = mock(DataServiceReader.class);
    DataServiceApiKeyRepository keyRepository = mock(DataServiceApiKeyRepository.class);
    DataServiceConsumerRepository consumerRepository = mock(DataServiceConsumerRepository.class);
    ApiKeySecretGenerator secrets = mock(ApiKeySecretGenerator.class);
    DataServiceRateLimiter rateLimiter = mock(DataServiceRateLimiter.class);
    DataServiceIpAccessAuthorizer ipAccessAuthorizer = mock(DataServiceIpAccessAuthorizer.class);
    DataServiceConsumerIpAccessAuthorizer consumerIpAccessAuthorizer =
        mock(DataServiceConsumerIpAccessAuthorizer.class);
    DataServiceQueryExecutor executor = mock(DataServiceQueryExecutor.class);
    DataServiceInvocationRecorder recorder = mock(DataServiceInvocationRecorder.class);
    ProjectContextScope projectContextScope = mock(ProjectContextScope.class);
    ActionAuthorization actionAuthorization = mock(ActionAuthorization.class);

    when(reader.requireByPath("/orders")).thenReturn(definition);
    when(projectContextScope.call(any(), any())).thenAnswer(invocation -> {
      Supplier<?> action = invocation.getArgument(1);
      return action.get();
    });
    when(consumerRepository.hasConfiguredAccess(3L, 7L)).thenReturn(true);
    when(secrets.hash("bad-key")).thenReturn("bad-hash");
    when(secrets.hash("yak-key")).thenReturn("hash");
    when(keyRepository.findByHash("bad-hash")).thenReturn(Optional.empty());

    LocalDateTime now = LocalDateTime.of(2026, 9, 26, 10, 0);
    DataServiceApiKey key = new DataServiceApiKey(
        5L, null, 21L, "Golden Key", "yak_gold", "hash", true, 60,
        null, null, now, now);
    DataServiceConsumer consumer = new DataServiceConsumer(
        21L, 3L, "Golden BI Consumer", null, ConsumerAccessScope.ALL, true, 60, now, now);
    when(keyRepository.findByHash("hash")).thenReturn(Optional.of(key));
    when(consumerRepository.findByIdForProject(21L, 3L)).thenReturn(Optional.of(consumer));
    when(consumerRepository.hasAccess(21L, 3L, 7L)).thenReturn(true);
    when(keyRepository.save(key)).thenReturn(key);
    doThrow(new DataServiceForbiddenException("当前来源 IP 不在调用方白名单中"))
        .when(consumerIpAccessAuthorizer)
        .authorize(21L, "10.0.0.9");

    DataServiceQueryResponse response = new DataServiceQueryResponse(
        List.of("id"), List.of(Map.of("id", 1L)), false, 1, 8L);
    when(executor.execute(eq(definition), any(), isNull())).thenReturn(response);

    DataServiceAuthorizer authorizer = new DataServiceAuthorizer(
        keyRepository,
        secrets,
        rateLimiter,
        ipAccessAuthorizer,
        consumerRepository,
        consumerIpAccessAuthorizer);
    DataServiceInvoker invoker = new DataServiceInvoker(
        reader,
        authorizer,
        new DataServiceSqlCompiler(),
        executor,
        new LocalDataServiceRuntime(),
        recorder,
        projectContextScope,
        actionAuthorization);

    assertThatThrownBy(() -> invoker.invoke("/orders", Map.of("id", "1"), null, "10.0.0.8"))
        .isInstanceOf(DataServiceUnauthorizedException.class)
        .hasMessageContaining("X-API-Key");

    assertThatThrownBy(() -> invoker.invoke("/orders", Map.of("id", "1"), "bad-key", "10.0.0.8"))
        .isInstanceOf(DataServiceUnauthorizedException.class)
        .hasMessageContaining("API Key 无效或无权访问当前 API");

    assertThatThrownBy(() -> invoker.invoke("/orders", Map.of("id", "1"), "yak-key", "10.0.0.9"))
        .isInstanceOf(DataServiceForbiddenException.class)
        .hasMessageContaining("IP");
    verify(executor, org.mockito.Mockito.never()).execute(any(), any(), any());

    DataServiceQueryResponse result =
        invoker.invoke("/orders", Map.of("id", "1"), "yak-key", "10.0.0.8");

    assertThat(result.rows()).containsExactly(Map.of("id", 1L));
    verify(actionAuthorization, org.mockito.Mockito.times(4))
        .requirePermissionIfAuthenticated("data-service:invoke");
    verify(consumerIpAccessAuthorizer).authorize(21L, "10.0.0.9");
    verify(consumerIpAccessAuthorizer).authorize(21L, "10.0.0.8");
    verify(rateLimiter).acquire(key);
    verify(executor).execute(eq(definition), any(), isNull());

    ArgumentCaptor<AccessContext> accessCaptor = ArgumentCaptor.forClass(AccessContext.class);
    verify(recorder).record(
        eq(definition), any(), eq(true), eq(8L), eq(1), isNull(), accessCaptor.capture());
    AccessContext access = accessCaptor.getValue();
    assertThat(access.callerType()).isEqualTo("API_KEY");
    assertThat(access.apiKeyId()).isEqualTo(5L);
    assertThat(access.consumerId()).isEqualTo(21L);
    assertThat(access.apiKeyName()).isEqualTo("Golden BI Consumer");
    assertThat(access.apiKeyPrefix()).isEqualTo("yak_gold");
  }

  private DataServiceDefinition definition() {
    return DataServiceDefinition.restore(
        7L,
        3L,
        11L,
        new DataServiceSettings("Orders", "/orders", 100, 30, true, null, false),
        new PublishedRuntimeSnapshot(9L, "select id from orders where id = :id"),
        new SourceReference("DATASET", "42", 101L, 4),
        new RuntimePolicy(false, 60, 100, false, 5, 30),
        AuthMode.NONE,
        LocalDateTime.of(2026, 9, 26, 9, 0),
        LocalDateTime.of(2026, 9, 26, 9, 0));
  }
}
