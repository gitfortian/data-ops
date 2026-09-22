package io.yak.ops.business.agent.config;

import io.agentscope.core.agui.encoder.AguiEventEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** AG-UI 协议编码器注册（官方化 v2：线上帧使用 AguiEventEncoder 序列化）。 */
@ConditionalOnAgentEnabled
@Configuration(proxyBeanMethods = false)
public class AgentAguiConfiguration {

  @Bean
  public AguiEventEncoder aguiEventEncoder() {
    return new AguiEventEncoder();
  }
}
