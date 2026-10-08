package io.yak.ops.boot.project;

import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ThreadLocalProjectContext;
import org.springframework.stereotype.Component;

/**
 * Spring / HTTP adapter for Core's trusted Project context.
 *
 * <p>Existing controllers, interceptors and background callers continue injecting
 * this bean or the CurrentProject / ProjectContextScope interfaces. Only Boot can
 * bind and clear a request's trusted context; no setter is exposed to domains.
 */
@Component
public class ProjectContextRuntime extends ThreadLocalProjectContext {

  void bind(ProjectContext context) {
    bindTrusted(context);
  }

  void clear() {
    clearTrusted();
  }
}
