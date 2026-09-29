package io.yak.ops.spi.resource;

/**
 * Platform-provided resource resolution capability.
 *
 * <p>Task plugins obtain this interface via
 * {@code TaskExecutionContext.capability(ResourceResolver.class)},
 * resolving resource management file references to locally accessible temp files.
 *
 * <p>The presence of {@code configJson.resourceId} indicates resource reference mode.
 * This convention should be documented in each TaskPlugin's Javadoc.
 */
public interface ResourceResolver {

  /**
   * Resolve the latest version of a resource file to a local temp directory.
   * Suitable for development / debugging.
   *
   * <p>The returned {@link ResolvedResource} implements {@link AutoCloseable};
   * callers must invoke {@link ResolvedResource#close()} after execution
   * to clean up temp files.
   */
  ResolvedResource resolve(long resourceId);

  /**
   * Resolve a specific version of a resource file to a local temp directory.
   * Suitable for production execution, ensuring version consistency.
   *
   * <p><strong>Stage 1 strategy</strong>: downloads the current latest version
   * and validates that its version matches the requested {@code version}.
   * If the version has been updated, an {@link IllegalStateException} is thrown.
   * Physical historical-version download requires storage-layer support (Stage 2).
   *
   * @throws IllegalStateException if the requested version is not available
   *                              or the current version does not match
   */
  ResolvedResource resolve(long resourceId, int version);
}
