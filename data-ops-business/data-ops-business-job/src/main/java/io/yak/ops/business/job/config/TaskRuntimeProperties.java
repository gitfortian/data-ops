package io.yak.ops.business.job.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Local plugin resource budget; durable workflow/attempt ownership stays with its business owner. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "yak.job.runtime")
public class TaskRuntimeProperties {
  private int maxConcurrent = 32;
  private int maxRetainedHandles = 4096;
  private long terminalRetentionMillis = 300000L;
}
