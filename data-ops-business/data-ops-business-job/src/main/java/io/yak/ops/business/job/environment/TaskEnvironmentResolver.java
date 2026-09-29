package io.yak.ops.business.job.environment;

import java.util.Map;

/** Runtime-facing read boundary for merged task environment variables. */
public interface TaskEnvironmentResolver {

  Map<String, String> resolveMergedEnv();
}
