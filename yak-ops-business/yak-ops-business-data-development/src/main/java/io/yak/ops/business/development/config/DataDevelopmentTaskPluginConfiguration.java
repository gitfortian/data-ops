package io.yak.ops.business.development.config;

import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Makes the platform TaskPlugin registry available to data-development authoring services. */
@Configuration(proxyBeanMethods = false)
public class DataDevelopmentTaskPluginConfiguration {

  @Bean
  @ConditionalOnMissingBean(TaskPluginRegistry.class)
  public TaskPluginRegistry taskPluginRegistry() {
    return TaskPluginRegistry.load();
  }
}
