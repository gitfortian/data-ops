package io.yak.ops.business.agent.config;

import io.agentscope.core.tracing.TracerRegistry;
import io.agentscope.core.tracing.telemetry.TelemetryTracer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry 分布式追踪：用官方 {@link TelemetryTracer} 注册进 {@link TracerRegistry}，
 * agent 的 model / tool / agent 调用自动产出 gen_ai.* span 并 OTLP 导出（Jaeger / Tempo）。
 *
 * <p>开关 {@code yak.agent.observability.otel.enabled=true} 时装配；默认关闭走 NoopTracer（零开销）。
 * 注册非 Noop Tracer 时 {@link TracerRegistry} 会自动安装 Reactor trace context 传播钩子。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAgentEnabled
@ConditionalOnProperty(
    prefix = "yak.agent.observability.otel",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class AgentObservabilityOtelConfiguration {

  @Bean(destroyMethod = "shutdown")
  public TelemetryTracer yakAgentTelemetryTracer(AgentProperties properties) {
    AgentProperties.Observability.Otel otel = properties.getObservability().getOtel();
    TelemetryTracer.Builder builder = TelemetryTracer.builder()
        .enabled(true)
        .endpoint(otel.getEndpoint());
    if (otel.getHeaders() != null && !otel.getHeaders().isEmpty()) {
      builder.headers(otel.getHeaders());
    }
    TelemetryTracer tracer = builder.build();
    TracerRegistry.register(tracer);
    return tracer;
  }
}
