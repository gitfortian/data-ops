package io.yak.ops.business.resource.resolution;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.spi.resource.ResolvedResource;
import io.yak.ops.spi.resource.ResourceDownloadProvider;
import io.yak.ops.spi.resource.ResourceDownloadResult;
import io.yak.ops.spi.resource.ResourceResolver;
import io.yak.ops.spi.resource.TempDirectoryUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

/** Materializes a Resource to a verified local temporary file for task runtimes. */
@Component
@ConditionalOnResourceEnabled
public class ResourceResolverAdapter implements ResourceResolver {

  private final ResourceDownloadProvider downloadProvider;

  public ResourceResolverAdapter(ResourceDownloadProvider downloadProvider) {
    this.downloadProvider = downloadProvider;
  }

  @Override
  public ResolvedResource resolve(long resourceId) {
    return doResolve(resourceId, downloadProvider.download(resourceId));
  }

  @Override
  public ResolvedResource resolve(long resourceId, int version) {
    return doResolve(resourceId, downloadProvider.download(resourceId, version));
  }

  private ResolvedResource doResolve(long resourceId, ResourceDownloadResult result) {
    Path tempDir = null;
    try {
      tempDir = Files.createTempDirectory("yak-task-");
      Path localFile = tempDir.resolve(result.fileName());
      String actualChecksum;
      try (InputStream inputStream = result.inputStream()) {
        actualChecksum = copyWithChecksum(inputStream, localFile);
      }
      if (result.checksum() != null
          && !result.checksum().isBlank()
          && !result.checksum().equalsIgnoreCase(actualChecksum)) {
        TempDirectoryUtils.deleteRecursively(tempDir);
        throw new IllegalStateException(
            "Resource #" + resourceId + " checksum mismatch: expected "
                + result.checksum() + ", actual " + actualChecksum);
      }
      return new ResolvedResource(
          localFile,
          result.fileName(),
          result.suffix(),
          result.fileSize(),
          actualChecksum);
    } catch (Exception exception) {
      if (tempDir != null) {
        try {
          TempDirectoryUtils.deleteRecursively(tempDir);
        } catch (IOException cleanupError) {
          exception.addSuppressed(cleanupError);
        }
      }
      throw new IllegalStateException(
          "Failed to resolve resource #" + resourceId + " to local file",
          exception);
    }
  }

  private static String copyWithChecksum(InputStream inputStream, Path target) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest);
          var outputStream = Files.newOutputStream(target)) {
        digestInputStream.transferTo(outputStream);
      }
      StringBuilder value = new StringBuilder(64);
      for (byte item : digest.digest()) {
        value.append(String.format("%02x", item & 0xff));
      }
      return value.toString();
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 not available", exception);
    }
  }
}
