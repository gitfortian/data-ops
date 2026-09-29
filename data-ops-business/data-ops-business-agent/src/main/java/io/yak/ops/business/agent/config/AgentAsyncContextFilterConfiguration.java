package io.yak.ops.business.agent.config;

import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Sa-Token 上下文过滤器默认仅注册 REQUEST dispatch；SSE（SseEmitter）完成后的
 * ASYNC dispatch 不经过它，导致安全拦截器在异步线程上拿不到上下文而抛
 * SaTokenContextException。此处为 agent 路径补一个仅 ASYNC 的窄注册，
 * 与官方 REQUEST 注册互补；上游修复后可整体移除。
 */
@ConditionalOnAgentEnabled
@Configuration
public class AgentAsyncContextFilterConfiguration {

  @Bean
  public FilterRegistrationBean<SaTokenContextFilterForJakartaServlet>
      agentSaTokenAsyncContextFilter() {
    FilterRegistrationBean<SaTokenContextFilterForJakartaServlet> registration =
        new FilterRegistrationBean<>(new SaTokenContextFilterForJakartaServlet());
    registration.setName("agentSaTokenContextFilterForAsyncDispatch");
    registration.setDispatcherTypes(DispatcherType.ASYNC);
    registration.addUrlPatterns("/api/v1/agent/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
