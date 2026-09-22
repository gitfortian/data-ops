package io.yak.ops.boot.audit;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.AuditOperationTypes;
import io.yak.ops.business.audit.AuditPayloadRedactor;
import io.yak.ops.business.audit.AuditWebLedger;
import io.yak.ops.business.audit.AuditWebOperationInfer;
import io.yak.ops.business.audit.AuditWebOperationInfer.InferredWebOperation;
import io.yak.ops.common.annotation.Auditable;
import io.yak.ops.business.audit.BusinessAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

/**
 * 写接口审计兜底：未被业务 SDK 开账的 HTTP 写请求，在请求结束时按推导/声明语义补记一条审计。
 * fail-open 复用业务审计 SDK；与手工埋点经 {@link AuditWebLedger} 合并防双记。
 */
@Component
public class AuditWebInterceptor implements HandlerInterceptor {

  private static final String START_ATTRIBUTE = AuditWebInterceptor.class.getName() + ".start";

  private final ObjectProvider<BusinessAuditService> auditServiceProvider;
  private final ObjectProvider<AuditPayloadRedactor> redactorProvider;

  public AuditWebInterceptor(
      ObjectProvider<BusinessAuditService> auditServiceProvider,
      ObjectProvider<AuditPayloadRedactor> redactorProvider) {
    this.auditServiceProvider = auditServiceProvider;
    this.redactorProvider = redactorProvider;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    request.setAttribute(START_ATTRIBUTE, System.currentTimeMillis());
    AuditWebLedger.beginRequest();
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
    try {
      recordFallback(request, response, handler, exception);
    } finally {
      AuditWebLedger.endRequest();
    }
  }

  private void recordFallback(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
    if (AuditWebOperationInfer.readOnlyMethod(request.getMethod())) {
      return;
    }
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return;
    }
    if (AuditWebLedger.hasSdkOperation()) {
      return;
    }
    Auditable declared =
        AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), Auditable.class);
    if (declared != null && declared.ignore()) {
      return;
    }
    String pattern = bestMatchingPattern(request);
    InferredWebOperation inferred =
        AuditWebOperationInfer.infer(request.getMethod().toUpperCase(), pattern);
    if (inferred == null) {
      return;
    }
    BusinessAuditService auditService = auditServiceProvider.getIfAvailable();
    if (auditService == null) {
      return;
    }

    String operationType =
        declared != null && !declared.type().isBlank() ? declared.type() : inferred.operationType();
    String operationName =
        declared != null && !declared.name().isBlank()
            ? declared.name()
            : inferred.operationName();
    String resourceType =
        declared != null && !declared.resourceType().isBlank()
            ? declared.resourceType()
            : inferred.resourceType();
    String resourceId = resolveResourceId(request, declared);
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("method", request.getMethod());
    metadata.put("path", pattern);
    metadata.put("httpStatus", response.getStatus());
    metadata.put("durationMs", durationMs(request));
    if (declared != null && declared.recordPayload()) {
      appendPayload(request, metadata);
    }

    AuditOperationHandle handle =
        auditService.start(
            new AuditOperationRequest(
                operationType,
                operationName,
                resourceType,
                resourceId,
                null,
                AuditOperationTypes.SOURCE_WEB,
                metadata));
    int status = response.getStatus();
    if (exception != null || status >= 400) {
      handle.failure(failureReason(exception, status), exception);
    } else {
      handle.success(operationName);
    }
  }

  private String resolveResourceId(HttpServletRequest request, Auditable declared) {
    Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (!(attribute instanceof Map<?, ?> variables) || variables.isEmpty()) {
      return null;
    }
    if (declared != null && !declared.resourceIdParam().isBlank()) {
      Object value = variables.get(declared.resourceIdParam());
      return value == null ? null : String.valueOf(value);
    }
    Object id = variables.get("id");
    if (id != null) {
      return String.valueOf(id);
    }
    return variables.size() == 1 ? String.valueOf(variables.values().iterator().next()) : null;
  }

  private void appendPayload(HttpServletRequest request, Map<String, Object> metadata) {
    AuditPayloadRedactor redactor = redactorProvider.getIfAvailable();
    if (redactor == null) {
      redactor = AuditPayloadRedactor.withDefaults(new com.fasterxml.jackson.databind.ObjectMapper());
    }
    ContentCachingRequestWrapper wrapper =
        WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
    if (wrapper != null) {
      String payload = redactor.redactJsonBody(wrapper.getContentAsByteArray());
      if (payload != null) {
        metadata.put("payload", payload);
        return;
      }
    }
    java.util.List<String> names = redactor.parameterNames(request.getParameterMap());
    if (!names.isEmpty()) {
      metadata.put("parameters", names);
    }
  }

  private String bestMatchingPattern(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (pattern instanceof String pathPattern && !pathPattern.isBlank()) {
      return pathPattern;
    }
    return request.getRequestURI();
  }

  private long durationMs(HttpServletRequest request) {
    Object start = request.getAttribute(START_ATTRIBUTE);
    return start instanceof Long started ? Math.max(0, System.currentTimeMillis() - started) : 0;
  }

  private String failureReason(Exception exception, int status) {
    if (exception != null) {
      return exception.getClass().getSimpleName();
    }
    return "HTTP_" + status;
  }
}
