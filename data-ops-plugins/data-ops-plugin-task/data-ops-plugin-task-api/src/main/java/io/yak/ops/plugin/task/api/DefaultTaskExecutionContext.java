package io.yak.ops.plugin.task.api;

import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-provided {@link TaskExecutionContext} implementation.
 *
 * <p>Centralises the boilerplate shared by every task adapter:
 * trigger normalisation, parameter immutability, global environment variable
 * injection, and capability resolution.  Task adapters should obtain instances
 * via {@link Builder} rather than writing their own private context records.
 *
 * <p>Environment variable merge priority (highest first):
 * <ol>
 *   <li>Task-level {@code envVars} from the task definition config</li>
 *   <li>Application-level env vars (from {@code globalEnvVars}, resolved by the factory)</li>
 *   <li>OS-level environment variables (already merged into {@code globalEnvVars})</li>
 * </ol>
 */
public final class DefaultTaskExecutionContext implements TaskExecutionContext {

  private final TaskExecutionTrigger trigger;
  private final Map<String, Object> parameters;
  private final Map<String, String> globalEnvVars;
  private final Map<Class<?>, Object> capabilities;

  private DefaultTaskExecutionContext(Builder builder) {
    this.trigger = builder.trigger == null
        ? TaskExecutionTrigger.WORKFLOW : builder.trigger;
    this.parameters = builder.parameters == null || builder.parameters.isEmpty()
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(builder.parameters));
    this.globalEnvVars = builder.globalEnvVars == null || builder.globalEnvVars.isEmpty()
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(builder.globalEnvVars));
    this.capabilities = builder.capabilities == null || builder.capabilities.isEmpty()
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(builder.capabilities));
  }

  @Override
  public TaskExecutionTrigger trigger() {
    return trigger;
  }

  @Override
  public Map<String, Object> parameters() {
    return parameters;
  }

  @Override
  public Map<String, String> globalEnvVars() {
    return globalEnvVars;
  }

  @Override
  public <T> Optional<T> capability(Class<T> capabilityType) {
    Objects.requireNonNull(capabilityType, "capabilityType");
    Object candidate = capabilities.get(capabilityType);
    if (candidate != null && capabilityType.isInstance(candidate)) {
      return Optional.of(capabilityType.cast(candidate));
    }
    // Also check sub-type matches for flexibility
    for (Object value : capabilities.values()) {
      if (capabilityType.isInstance(value)) {
        return Optional.of(capabilityType.cast(value));
      }
    }
    return Optional.empty();
  }

  /** Create a new builder pre-populated with this context's values. */
  public Builder toBuilder() {
    return new Builder()
        .trigger(trigger)
        .parameters(parameters)
        .globalEnvVars(globalEnvVars)
        .capabilities(capabilities);
  }

  /** Create an empty builder. */
  public static Builder builder() {
    return new Builder();
  }

  /** Fluent builder for {@link DefaultTaskExecutionContext}. */
  public static final class Builder {
    private TaskExecutionTrigger trigger;
    private Map<String, Object> parameters;
    private Map<String, String> globalEnvVars;
    private Map<Class<?>, Object> capabilities;

    private Builder() {}

    public Builder trigger(TaskExecutionTrigger trigger) {
      this.trigger = trigger;
      return this;
    }

    public Builder parameters(Map<String, Object> parameters) {
      this.parameters = parameters;
      return this;
    }

    public Builder globalEnvVars(Map<String, String> globalEnvVars) {
      this.globalEnvVars = globalEnvVars;
      return this;
    }

    /** Register a capability by its public interface type. */
    public <T> Builder capability(Class<T> type, T instance) {
      if (this.capabilities == null) {
        this.capabilities = new LinkedHashMap<>();
      }
      this.capabilities.put(type, instance);
      return this;
    }

    Builder capabilities(Map<Class<?>, Object> capabilities) {
      this.capabilities = capabilities;
      return this;
    }

    public DefaultTaskExecutionContext build() {
      return new DefaultTaskExecutionContext(this);
    }
  }
}
