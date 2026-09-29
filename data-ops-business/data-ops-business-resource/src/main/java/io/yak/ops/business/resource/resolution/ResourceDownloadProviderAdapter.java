package io.yak.ops.business.resource.resolution;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.content.ResourceContentReader;
import io.yak.ops.business.resource.domain.ResourceDownload;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.namespace.ResourceNamespaceReader;
import io.yak.ops.spi.resource.ResourceDownloadProvider;
import io.yak.ops.spi.resource.ResourceDownloadResult;
import org.springframework.stereotype.Component;

/** Platform adapter exposing Resource content through the cross-module download SPI. */
@Component
@ConditionalOnResourceEnabled
public class ResourceDownloadProviderAdapter implements ResourceDownloadProvider {

  private final ResourceNamespaceReader namespace;
  private final ResourceContentReader content;

  public ResourceDownloadProviderAdapter(
      ResourceNamespaceReader namespace,
      ResourceContentReader content) {
    this.namespace = namespace;
    this.content = content;
  }

  @Override
  public ResourceDownloadResult download(long resourceId) {
    ResourceNode node = namespace.requireFile(resourceId);
    return toResult(node, content.download(resourceId));
  }

  @Override
  public ResourceDownloadResult download(long resourceId, int version) {
    ResourceNode node = namespace.requireFile(resourceId);
    if (node.getVersion() == null) {
      throw new IllegalStateException(
          "Resource #" + resourceId + " has no version metadata; cannot verify requested version "
              + version);
    }
    if (node.getVersion() != version) {
      throw new IllegalStateException(
          "Resource #" + resourceId + " version mismatch: requested version "
              + version + ", current version " + node.getVersion()
              + ". The resource has been updated since the task was published.");
    }
    return toResult(node, content.download(resourceId));
  }

  private ResourceDownloadResult toResult(ResourceNode node, ResourceDownload download) {
    return new ResourceDownloadResult(
        download.fileName(),
        node.getSuffix() != null ? node.getSuffix() : "",
        download.fileSize(),
        node.getChecksum() != null ? node.getChecksum() : "",
        node.getVersion() != null ? node.getVersion() : 0,
        download.inputStream());
  }
}
