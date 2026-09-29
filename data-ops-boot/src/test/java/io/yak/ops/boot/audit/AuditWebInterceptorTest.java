package io.yak.ops.boot.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.AuditPayloadRedactor;
import io.yak.ops.business.audit.AuditWebLedger;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.common.annotation.Auditable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Web 审计兜底拦截器行为契约（票 02）：兜底写入、SDK 合并防双记、注解覆盖、终态判定。 */
class AuditWebInterceptorTest {

  private RecordingAuditService auditService;
  private AuditWebInterceptor interceptor;

  @BeforeEach
  void setUp() {
    auditService = new RecordingAuditService();
    interceptor =
        new AuditWebInterceptor(
            providerOf(auditService), providerOfRedactor(AuditPayloadRedactor.withDefaults(new com.fasterxml.jackson.databind.ObjectMapper())));
  }

  @AfterEach
  void tearDown() {
    AuditWebLedger.endRequest();
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<BusinessAuditService> providerOf(BusinessAuditService service) {
    ObjectProvider<BusinessAuditService> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(service);
    return provider;
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<AuditPayloadRedactor> providerOfRedactor(AuditPayloadRedactor redactor) {
    ObjectProvider<AuditPayloadRedactor> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(redactor);
    return provider;
  }

  @Test
  void recordsFallbackOperationForInferredWriteEndpoint() throws Exception {
    MockHttpServletRequest request = writeRequest("POST", "/api/v1/modeling/models");
    interceptor.preHandle(request, new MockHttpServletResponse(), handler("plainCreate"));

    interceptor.afterCompletion(request, new MockHttpServletResponse(), handler("plainCreate"), null);

    assertThat(auditService.requests).hasSize(1);
    AuditOperationRequest recorded = auditService.requests.get(0);
    assertThat(recorded.operationType()).isEqualTo("MODELING_MODELS_CREATE");
    assertThat(recorded.operationName()).isEqualTo("新增 modeling/models");
    assertThat(recorded.resourceType()).isEqualTo("MODELING_MODELS");
    assertThat(recorded.source()).isEqualTo("WEB");
    assertThat(recorded.metadata().get("method")).isEqualTo("POST");
    assertThat(recorded.metadata().get("path")).isEqualTo("/api/v1/modeling/models");
    assertThat(auditService.handles.get(0).successSummary).isNotNull();
    assertThat(auditService.handles.get(0).failureReason).isNull();
  }

  @Test
  void skipsReadOnlyMethods() throws Exception {
    MockHttpServletRequest request = writeRequest("GET", "/api/v1/modeling/models");
    interceptor.preHandle(request, new MockHttpServletResponse(), handler("plainCreate"));
    interceptor.afterCompletion(request, new MockHttpServletResponse(), handler("plainCreate"), null);
    assertThat(auditService.requests).isEmpty();
  }

  @Test
  void skipsWhenBusinessSdkAlreadyOpenedOperation() throws Exception {
    MockHttpServletRequest request = writeRequest("POST", "/api/v1/modeling/models");
    interceptor.preHandle(request, new MockHttpServletResponse(), handler("plainCreate"));
    AuditWebLedger.markSdkOperationOpened(); // 模拟业务代码调用过 BusinessAuditService.start()

    interceptor.afterCompletion(request, new MockHttpServletResponse(), handler("plainCreate"), null);

    assertThat(auditService.requests).isEmpty();
  }

  @Test
  void skipsReadLikePostPaths() throws Exception {
    MockHttpServletRequest request = writeRequest("POST", "/api/v1/modeling/models/page");
    interceptor.preHandle(request, new MockHttpServletResponse(), handler("plainCreate"));
    interceptor.afterCompletion(request, new MockHttpServletResponse(), handler("plainCreate"), null);
    assertThat(auditService.requests).isEmpty();
  }

  @Test
  void annotationOverridesInferredSemantics() throws Exception {
    MockHttpServletRequest request = writeRequest("PUT", "/api/v1/modeling/models/{id}");
    request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "77"));
    interceptor.preHandle(request, new MockHttpServletResponse(), handler("declaredUpdate"));

    interceptor.afterCompletion(request, new MockHttpServletResponse(), handler("declaredUpdate"), null);

    assertThat(auditService.requests).hasSize(1);
    AuditOperationRequest recorded = auditService.requests.get(0);
    assertThat(recorded.operationType()).isEqualTo("MODELING_MODEL_UPDATE");
    assertThat(recorded.operationName()).isEqualTo("更新模型");
    assertThat(recorded.resourceType()).isEqualTo("MODEL");
    assertThat(recorded.resourceId()).isEqualTo("77");
  }

  @Test
  void ignoreAnnotationSuppressesFallback() throws Exception {
    MockHttpServletRequest request = writeRequest("POST", "/api/v1/modeling/models/bulk-heartbeat");
    interceptor.preHandle(request, new MockHttpServletResponse(), handler("ignored"));
    interceptor.afterCompletion(request, new MockHttpServletResponse(), handler("ignored"), null);
    assertThat(auditService.requests).isEmpty();
  }

  @Test
  void marksFailedOnHttpStatus() throws Exception {
    MockHttpServletRequest request = writeRequest("DELETE", "/api/v1/modeling/models/{id}");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(404);
    interceptor.preHandle(request, response, handler("plainCreate"));
    interceptor.afterCompletion(request, response, handler("plainCreate"), null);

    assertThat(auditService.requests).hasSize(1);
    assertThat(auditService.handles.get(0).failureReason).isEqualTo("HTTP_404");
  }

  @Test
  void marksFailedWithExceptionTypeWhenHandlerThrows() throws Exception {
    MockHttpServletRequest request = writeRequest("POST", "/api/v1/modeling/models");
    interceptor.preHandle(request, new MockHttpServletResponse(), handler("plainCreate"));
    interceptor.afterCompletion(
        request, new MockHttpServletResponse(), handler("plainCreate"),
        new IllegalStateException("boom"));

    assertThat(auditService.handles.get(0).failureReason).isEqualTo("IllegalStateException");
    assertThat(auditService.handles.get(0).failureCause).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void nonHandlerMethodTargetIsSkipped() throws Exception {
    MockHttpServletRequest request = writeRequest("POST", "/error");
    interceptor.preHandle(request, new MockHttpServletResponse(), "not-a-handler-method");
    interceptor.afterCompletion(request, new MockHttpServletResponse(), "not-a-handler-method", null);
    assertThat(auditService.requests).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void missingAuditServiceBeanStaysSilent() throws Exception {
    ObjectProvider<BusinessAuditService> empty = mock(ObjectProvider.class);
    when(empty.getIfAvailable()).thenReturn(null);
    AuditWebInterceptor lazy =
        new AuditWebInterceptor(empty, providerOfRedactor(null));
    MockHttpServletRequest request = writeRequest("POST", "/api/v1/modeling/models");
    lazy.preHandle(request, new MockHttpServletResponse(), handler("plainCreate"));
    lazy.afterCompletion(request, new MockHttpServletResponse(), handler("plainCreate"), null);
    // 不抛异常即通过；ledger 由 finally 清理，见下一用例
  }

  @Test
  void recordPayloadStoresRedactedBody() throws Exception {
    MockHttpServletRequest inner = writeRequest("POST", "/api/v1/modeling/models");
    inner.setContent("{\"name\":\"m1\",\"password\":\"s3cret\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    org.springframework.web.util.ContentCachingRequestWrapper request =
        new org.springframework.web.util.ContentCachingRequestWrapper(inner);
    request.getInputStream().readAllBytes(); // 模拟业务已消费请求体

    interceptor.preHandle(request, new MockHttpServletResponse(), handler("payloadCreate"));
    interceptor.afterCompletion(request, new MockHttpServletResponse(), handler("payloadCreate"), null);

    assertThat(auditService.requests).hasSize(1);
    String payload = (String) auditService.requests.get(0).metadata().get("payload");
    assertThat(payload).contains("\"password\":\"***\"").contains("m1").doesNotContain("s3cret");
  }

  @Test
  void payloadAbsentUnlessDeclared() throws Exception {
    MockHttpServletRequest inner = writeRequest("POST", "/api/v1/modeling/models");
    inner.setContent("{\"name\":\"m1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    org.springframework.web.util.ContentCachingRequestWrapper request =
        new org.springframework.web.util.ContentCachingRequestWrapper(inner);
    request.getInputStream().readAllBytes();

    interceptor.preHandle(request, new MockHttpServletResponse(), handler("plainCreate"));
    interceptor.afterCompletion(request, new MockHttpServletResponse(), handler("plainCreate"), null);

    assertThat(auditService.requests.get(0).metadata()).doesNotContainKey("payload");
  }

  @Test
  void ledgerStateNeverLeaksAfterCompletion() throws Exception {
    MockHttpServletRequest request = writeRequest("POST", "/api/v1/modeling/models");
    interceptor.preHandle(request, new MockHttpServletResponse(), handler("plainCreate"));
    interceptor.afterCompletion(request, new MockHttpServletResponse(), handler("plainCreate"), null);

    // 线程复用后新线程未 beginRequest 时，SDK 打标不得生效
    assertThat(AuditWebLedger.hasSdkOperation()).isFalse();
    AuditWebLedger.markSdkOperationOpened();
    assertThat(AuditWebLedger.hasSdkOperation()).isFalse();
  }

  @Test
  void configurationCarriesKillSwitchContract() {
    org.springframework.boot.autoconfigure.condition.ConditionalOnProperty conditional =
        AuditWebConfiguration.class.getAnnotation(
            org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);
    assertThat(conditional).isNotNull();
    assertThat(conditional.prefix()).isEqualTo("yak.audit.web");
    assertThat(conditional.name()).containsExactly("enabled");
    assertThat(conditional.matchIfMissing()).isTrue();
    // 开关关闭 => 配置类不装配 => 拦截器与 Filter 均不注册，兜底零写入
  }

  private MockHttpServletRequest writeRequest(String method, String pattern) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, pattern);
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
    return request;
  }

  private HandlerMethod handler(String methodName) throws Exception {
    return new HandlerMethod(new SampleController(), SampleController.class.getMethod(methodName));
  }

  /** 被测注解形态的样例端点。 */
  static class SampleController {

    public void plainCreate() {}

    @Auditable(name = "更新模型", type = "MODELING_MODEL_UPDATE", resourceType = "MODEL")
    public void declaredUpdate() {}

    @Auditable(recordPayload = true)
    public void payloadCreate() {}

    @Auditable(ignore = true)
    public void ignored() {}
  }

  private static class RecordingAuditService implements BusinessAuditService {
    final List<AuditOperationRequest> requests = new ArrayList<>();
    final List<RecordingHandle> handles = new ArrayList<>();

    @Override
    public AuditOperationHandle start(AuditOperationRequest request) {
      // 模拟 SDK 真实行为：开账即打标（防止后续二次兜底）
      AuditWebLedger.markSdkOperationOpened();
      requests.add(request);
      RecordingHandle handle = new RecordingHandle();
      handles.add(handle);
      return handle;
    }
  }

  private static class RecordingHandle implements AuditOperationHandle {
    String successSummary;
    String failureReason;
    Throwable failureCause;

    @Override
    public String operationId() {
      return "AUD-TEST";
    }

    @Override
    public void resource(String resourceId, String resourceName) {}

    @Override
    public void event(io.yak.ops.business.audit.AuditEventType type, String message, Map<String, ?> payload) {}

    @Override
    public void success(String summary) {
      this.successSummary = summary;
    }

    @Override
    public void failure(String reasonCode, Throwable cause) {
      this.failureReason = reasonCode;
      this.failureCause = cause;
    }
  }
}
