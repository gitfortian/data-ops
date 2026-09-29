package io.yak.ops.spi.resource;

import java.io.InputStream;

/**
 * Minimal abstraction for downloading resource files.
 *
 * <p>Platform implementations delegate to {@code ResourceService.download()},
 * decoupling {@link ResourceResolver} from the resource business module.
 */
public interface ResourceDownloadProvider {

  /**
   * Download the latest version of a resource file.
   */
  ResourceDownloadResult download(long resourceId);

  /**
   * Download a specific version of a resource file.
   *
   * <p><strong>Stage 1 strategy</strong>: downloads the current latest version
   * and includes its metadata so the caller can validate the version matches.
   *
   * @throws IllegalStateException if the resource or version is not available
   */
  ResourceDownloadResult download(long resourceId, int version);
}
