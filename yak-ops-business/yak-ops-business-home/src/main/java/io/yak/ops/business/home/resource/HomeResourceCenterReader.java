package io.yak.ops.business.home.resource;

import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceTreeNode;
import io.yak.ops.business.resource.namespace.ResourceTreeReader;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 首页文件资源只读聚合。 */
@Component
public class HomeResourceCenterReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(HomeResourceCenterReader.class);
  private static final int RECENT_DAYS = 7;
  private static final int RECENT_FILE_LIMIT = 3;

  private final ObjectProvider<ResourceTreeReader> resourceTreeReaderProvider;

  public HomeResourceCenterReader(ObjectProvider<ResourceTreeReader> resourceTreeReaderProvider) {
    this.resourceTreeReaderProvider = resourceTreeReaderProvider;
  }

  public OverviewResponse overview() {
    ResourceTreeReader treeReader = resourceTreeReaderProvider.getIfAvailable();
    if (treeReader == null) return unavailable();

    try {
      return aggregate(treeReader.tree(), LocalDateTime.now());
    } catch (RuntimeException exception) {
      LOGGER.warn("加载首页资源中心总览失败", exception);
      return unavailable();
    }
  }

  OverviewResponse aggregate(List<ResourceTreeNode> roots, LocalDateTime now) {
    List<ResourceNode> resources = flatten(roots);
    long fileCount = 0L;
    long folderCount = 0L;
    long totalBytes = 0L;
    List<TimedResource> recentCandidates = new ArrayList<>();
    LocalDateTime recentBoundary = now.minusDays(RECENT_DAYS);

    for (ResourceNode resource : resources) {
      if (resource == null) continue;
      if (resource.getNodeType() == ResourceNodeType.DIRECTORY) {
        folderCount += 1L;
        continue;
      }
      if (resource.getNodeType() != ResourceNodeType.FILE) continue;

      fileCount += 1L;
      totalBytes += Math.max(0L, resource.getFileSize() == null ? 0L : resource.getFileSize());

      LocalDateTime updatedAt = resourceUpdatedAt(resource);
      if (updatedAt != null && !updatedAt.isBefore(recentBoundary)) {
        recentCandidates.add(new TimedResource(resource, updatedAt));
      }
    }

    List<RecentFile> recentFiles =
        recentCandidates.stream()
            .sorted(Comparator.comparing(TimedResource::updatedAt).reversed())
            .limit(RECENT_FILE_LIMIT)
            .map(candidate -> recentFile(candidate.resource(), candidate.updatedAt()))
            .toList();

    return new OverviewResponse(true, fileCount, folderCount, totalBytes, recentFiles);
  }

  private List<ResourceNode> flatten(List<ResourceTreeNode> roots) {
    List<ResourceNode> resources = new ArrayList<>();
    if (roots == null) return resources;
    roots.forEach(root -> collect(root, resources));
    return resources;
  }

  private void collect(ResourceTreeNode node, List<ResourceNode> resources) {
    if (node == null) return;
    if (node.resource() != null) resources.add(node.resource());
    node.children().forEach(child -> collect(child, resources));
  }

  private LocalDateTime resourceUpdatedAt(ResourceNode resource) {
    return resource.getUpdateTime() == null ? resource.getCreateTime() : resource.getUpdateTime();
  }

  private RecentFile recentFile(ResourceNode resource, LocalDateTime updatedAt) {
    return new RecentFile(
        String.valueOf(resource.getId()),
        defaultText(resource.getName(), "未命名文件"),
        resource.getFullPath(),
        resource.getSuffix(),
        Math.max(0L, resource.getFileSize() == null ? 0L : resource.getFileSize()),
        updatedAt.toString());
  }

  private OverviewResponse unavailable() {
    return new OverviewResponse(false, null, null, null, List.of());
  }

  private static String defaultText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  public record OverviewResponse(
      boolean available,
      Long fileCount,
      Long folderCount,
      Long totalBytes,
      List<RecentFile> recentFiles) {}

  public record RecentFile(
      String id,
      String name,
      String fullPath,
      String suffix,
      Long fileSize,
      String updatedAt) {}

  private record TimedResource(ResourceNode resource, LocalDateTime updatedAt) {}
}
