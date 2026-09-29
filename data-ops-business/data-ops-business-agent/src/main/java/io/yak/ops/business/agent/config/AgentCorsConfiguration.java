package io.yak.ops.business.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Agent 接口开发期直连支持：允许 dev 前端（如 http://localhost:8000）绕过
 * dev server 代理直调 8080，规避代理层对 SSE 的 gzip 攒批。
 * 允许来源用属性注入，默认放行常见本地端口；生产保持同源不受影响。
 */
@ConditionalOnAgentEnabled
@Configuration
public class AgentCorsConfiguration implements WebMvcConfigurer {

  private final AgentProperties properties;

  public AgentCorsConfiguration(AgentProperties properties) {
    this.properties = properties;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    String patterns = properties.getCorsAllowedOriginPatterns();
    if (patterns == null || patterns.isBlank()) {
      return;
    }
    registry
        .addMapping("/api/v1/agent/**")
        .allowedOriginPatterns(patterns.split(","))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
