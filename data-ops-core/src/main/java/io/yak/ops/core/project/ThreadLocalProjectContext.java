package io.yak.ops.core.project;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Framework-independent Project context storage for a single execution thread.
 *
 * <p>The active project remains a trusted server-side fact. Only an explicit adapter
 * (for example the Boot HTTP boundary) may bind request context; normal callers
 * receive only {@link CurrentProject} and {@link ProjectContextScope}.
 *
 * <p>A background call temporarily overrides the current context and always restores
 * the prior binding, including when the action throws. No context is inherited across
 * threads; scheduled work must restore its persisted Project context explicitly.
 */
public abstract class ThreadLocalProjectContext implements CurrentProject, ProjectContextScope {

  private final ThreadLocal<ProjectContext> holder = new ThreadLocal<>();

  @Override
  public final Optional<ProjectContext> current() {
    return Optional.ofNullable(holder.get());
  }

  @Override
  public final <T> T call(ProjectContext context, Supplier<T> action) {
    if (context == null) throw new IllegalArgumentException("project context must not be null");
    if (action == null) throw new IllegalArgumentException("project action must not be null");

    ProjectContext previous = holder.get();
    holder.set(context);
    try {
      return action.get();
    } finally {
      if (previous == null) holder.remove();
      else holder.set(previous);
    }
  }

  /** Request-adapter operation; deliberately not part of the public Project contract. */
  protected final void bindTrusted(ProjectContext context) {
    holder.set(context);
  }

  /** Must be called by request-adapter completion and before binding a new request. */
  protected final void clearTrusted() {
    holder.remove();
  }
}
