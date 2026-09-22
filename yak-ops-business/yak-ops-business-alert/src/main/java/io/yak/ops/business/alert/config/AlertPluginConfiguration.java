package io.yak.ops.business.alert.config;

import io.yak.ops.core.plugin.alert.AlertPluginRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 注册告警插件 Registry 为 Spring Bean。 */
@Configuration(proxyBeanMethods = false)
public class AlertPluginConfiguration {

  @Bean
  @ConditionalOnMissingBean(AlertPluginRegistry.class)
  public AlertPluginRegistry alertPluginRegistry() {
    return AlertPluginRegistry.load();
  }
}
