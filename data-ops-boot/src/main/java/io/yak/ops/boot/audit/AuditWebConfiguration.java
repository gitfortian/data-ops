package io.yak.ops.boot.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.audit.AuditPayloadRedactor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 装配 Web 审计兜底（票 02/03）。开关 yak.audit.web.enabled，默认开启。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "yak.audit.web",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(AuditWebProperties.class)
public class AuditWebConfiguration implements WebMvcConfigurer {

  private final AuditWebInterceptor auditWebInterceptor;
  private final AuditWebProperties auditWebProperties;

  public AuditWebConfiguration(
      AuditWebInterceptor auditWebInterceptor, AuditWebProperties auditWebProperties) {
    this.auditWebInterceptor = auditWebInterceptor;
    this.auditWebProperties = auditWebProperties;
  }

  @Bean
  public AuditPayloadRedactor auditPayloadRedactor(ObjectMapper objectMapper) {
    return new AuditPayloadRedactor(objectMapper, auditWebProperties.getRedactFields());
  }

  @Bean
  public FilterRegistrationBean<AuditPayloadCaptureFilter> auditPayloadCaptureFilter() {
    FilterRegistrationBean<AuditPayloadCaptureFilter> registration =
        new FilterRegistrationBean<>(new AuditPayloadCaptureFilter());
    registration.addUrlPatterns("/api/*");
    registration.setOrder(org.springframework.core.Ordered.LOWEST_PRECEDENCE - 100);
    return registration;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // 晚于 ProjectScopeInterceptor(order 默认 0)：preHandle 后跑、afterCompletion 先跑，
    // 保证兜底记录时 project/认证上下文仍在位
    InterceptorRegistration registration =
        registry.addInterceptor(auditWebInterceptor).order(100).addPathPatterns("/api/**");
    registration.excludePathPatterns(auditWebProperties.getExcludePaths().toArray(String[]::new));
  }
}
