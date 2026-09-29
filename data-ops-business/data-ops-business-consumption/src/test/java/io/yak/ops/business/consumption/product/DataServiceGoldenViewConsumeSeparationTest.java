package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductDetail;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService;
import io.yak.ops.business.consumption.product.discovery.DataProductRegistry;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.consumption.product.provider.source.DataServiceDataProductProvider;
import io.yak.ops.business.dataservice.access.ApiKeySecretGenerator;
import io.yak.ops.business.dataservice.access.DataServiceAuthorizer;
import io.yak.ops.business.dataservice.access.DataServiceConsumerIpAccessAuthorizer;
import io.yak.ops.business.dataservice.access.DataServiceIpAccessAuthorizer;
import io.yak.ops.business.dataservice.access.DataServiceRateLimiter;
import io.yak.ops.business.dataservice.access.DataServiceUnauthorizedException;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.execution.DataServiceInvocationRecorder;
import io.yak.ops.business.dataservice.execution.DataServiceInvoker;
import io.yak.ops.business.dataservice.execution.DataServiceQueryExecutor;
import io.yak.ops.business.dataservice.execution.DataServiceSqlCompiler;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
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

/** Phase 4 #104: discovering/viewing a Data Service never grants public invoke access. */
class DataServiceGoldenViewConsumeSeparationTest {

  @Test
  void canonicalViewRemainsAvailableWhenPublicInvokeIsDeniedByApiKeyBoundary() {
    ProductKey key = new ProductKey(ProductType.DATA_SERVICE, "88");
    DataServiceDefinition definition = definition();
    DataServiceReader reader = mock(DataServiceReader.class);
    DataServiceViewFactory viewFactory = mock(DataServiceViewFactory.class);
    AssetSourceLookupService assetLookup = mock(AssetSourceLookupService.class);
    AssetAppService assetAppService = mock(AssetAppService.class);

    when(reader.require(88L)).thenReturn(definition);
    when(viewFactory.view(definition)).thenReturn(view());
    when(assetLookup.lookup("DATA_SERVICE", "88")).thenReturn(
        new AssetSourceLookupService.SourceLookup(
            "NOT_INDEXED", "DATA_SERVICE", "88", null, null, null, null));

    CanonicalProductService canonical = new CanonicalProductService(
        new ProductDiscoveryService(new DataProductRegistry(List.of(
            new DataServiceDataProductProvider(reader, viewFactory, assetLookup)))),
        assetAppService);

    CanonicalProductDetail beforeInvoke = canonical.detail(key);
    assertEquals(ProductLookupState.FOUND, beforeInvoke.state());
    assertEquals(key, beforeInvoke.product().productKey());

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
    when(consumerRepository.hasConfiguredAccess(7L, 88L)).thenReturn(true);
    when(secrets.hash("invalid-key")).thenReturn("invalid-hash");
    when(keyRepository.findByHash("invalid-hash")).thenReturn(Optional.empty());

    DataServiceInvoker invoker = new DataServiceInvoker(
        reader,
        new DataServiceAuthorizer(
            keyRepository,
            secrets,
            rateLimiter,
            ipAccessAuthorizer,
            consumerRepository,
            consumerIpAccessAuthorizer),
        new DataServiceSqlCompiler(),
        executor,
        new LocalDataServiceRuntime(),
        recorder,
        projectContextScope,
        actionAuthorization);

    DataServiceUnauthorizedException denied = assertThrows(
        DataServiceUnauthorizedException.class,
        () -> invoker.invoke("/orders", Map.of("tenantId", "acme"), "invalid-key", "10.0.0.8"));

    assertEquals("API Key 无效或无权访问当前 API", denied.getMessage());
    verify(actionAuthorization).requirePermissionIfAuthenticated("data-service:invoke");
    verify(executor, never()).execute(any(), any(), any());

    CanonicalProductDetail afterDeniedInvoke = canonical.detail(key);
    assertEquals(ProductLookupState.FOUND, afterDeniedInvoke.state());
    assertEquals(key, afterDeniedInvoke.product().productKey());
  }

  private DataServiceDefinition definition() {
    LocalDateTime observedAt = LocalDateTime.of(2026, 9, 26, 15, 0);
    return DataServiceDefinition.restore(
        88L,
        7L,
        4L,
        new DataServiceSettings(
            "Orders API", "/orders", 500, 30, true, "Published orders service", true),
        new PublishedRuntimeSnapshot(3L, "select * from orders where tenant_id=:tenantId"),
        new SourceReference("DEVELOPMENT_TASK", "task-55", 9001L, 12),
        RuntimePolicy.defaults(true),
        AuthMode.API_KEY,
        observedAt,
        observedAt);
  }

  private DataServiceView view() {
    LocalDateTime observedAt = LocalDateTime.of(2026, 9, 26, 15, 0);
    return new DataServiceView(
        88L,
        "Orders API",
        "/orders",
        "/api/v1/data-service/runtime/orders",
        3L,
        "select * from orders where tenant_id=:tenantId",
        List.of("tenantId"),
        500,
        30,
        true,
        "API_KEY",
        "Published orders service",
        "DEVELOPMENT_TASK",
        "task-55",
        9001L,
        12,
        observedAt,
        observedAt,
        true);
  }
}
