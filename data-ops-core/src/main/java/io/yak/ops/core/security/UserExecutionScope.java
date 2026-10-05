package io.yak.ops.core.security;

import java.util.function.Supplier;

/** Restores a live user and authorized project for trusted, persisted background work. */
public interface UserExecutionScope {
  <T> T call(long userId, long projectId, Supplier<T> action);
}
