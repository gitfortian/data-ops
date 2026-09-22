package io.yak.ops.spi.resource;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A resource file resolved to a local temp path.
 *
 * <p>Convention: the {@code localPath} must reside inside a dedicated temporary directory
 * (e.g. created by {@link java.nio.file.Files#createTempDirectory}). When {@link #close()}
 * is called, the <strong>entire parent directory</strong> is recursively deleted.
 *
 * @param localPath temp file path (inside a dedicated temp directory)
 * @param fileName  original file name
 * @param suffix    file suffix (e.g. "jar", "py", "sh")
 * @param fileSize  file size in bytes
 * @param checksum  SHA-256 checksum for integrity verification
 */
public record ResolvedResource(
    Path localPath,
    String fileName,
    String suffix,
    long fileSize,
    String checksum
) implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(ResolvedResource.class);

  /**
   * Recursively deletes the parent directory of {@code localPath}.
   *
   * <p>This is a cleanup operation — deletion failures are logged but never propagated,
   * to avoid masking the primary execution result. Callers must ensure that {@code localPath}
   * was placed inside a dedicated temp directory; otherwise unintended files may be deleted.
   */
  @Override
  public void close() {
    if (localPath == null) return;
    try {
      Path parent = localPath.getParent();
      if (parent != null) {
        TempDirectoryUtils.deleteRecursively(parent);
      }
    } catch (Exception e) {
      log.warn("Failed to cleanup temp directory for resource '{}': {}",
          fileName, e.getMessage());
    }
  }
}
