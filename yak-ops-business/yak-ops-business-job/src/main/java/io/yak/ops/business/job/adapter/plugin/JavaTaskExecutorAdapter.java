package io.yak.ops.business.job.adapter.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.runtime.AbstractTaskExecutorAdapter;
import io.yak.ops.business.job.runtime.TaskExecutionContextFactory;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.DefaultTaskExecutionContext;
import io.yak.ops.spi.resource.ResourceResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Executes Java JAR revisions through the shared Task Runtime and TaskPlugin contract. */
@Component
public class JavaTaskExecutorAdapter extends AbstractTaskExecutorAdapter {

  private static final String TYPE = "JAVA";

  @Autowired
  public JavaTaskExecutorAdapter(
      TaskPluginRegistry pluginRegistry,
      ObjectProvider<ResourceResolver> resourceResolverProvider,
      ObjectMapper objectMapper,
      TaskExecutionContextFactory contextFactory) {
    super(pluginRegistry, resourceResolverProvider, objectMapper, contextFactory);
  }

  @Override
  public String taskType() {
    return TYPE;
  }

  @Override
  protected String executionIdPrefix() {
    return "java";
  }

  @Override
  protected String displayName() {
    return "Java";
  }

  /** Java always requires ResourceResolver (resource-only, no inline content). */
  @Override
  protected void configureContext(
      DefaultTaskExecutionContext.Builder builder,
      String definitionJson) {
    ResourceResolver resolver = resourceResolver();
    if (resolver == null) {
      throw new IllegalStateException(
          "ResourceResolver capability is not available; "
              + "ensure the resource management module is enabled");
    }
    builder.capability(ResourceResolver.class, resolver);
  }
}
