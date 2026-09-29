package io.yak.ops.business.home.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceTreeNode;
import io.yak.ops.business.resource.namespace.ResourceTreeReader;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class HomeResourceCenterReaderTest {

  @Test
  void shouldAggregateResourceSummaryAndRecentFiles() {
    @SuppressWarnings("unchecked")
    ObjectProvider<ResourceTreeReader> provider = mock(ObjectProvider.class);
    HomeResourceCenterReader reader = new HomeResourceCenterReader(provider);
    LocalDateTime now = LocalDateTime.of(2026, 9, 9, 9, 0);

    ResourceNode folder = resource(1L, "SQL", ResourceNodeType.DIRECTORY, null, now.minusDays(20));
    ResourceNode newest = resource(2L, "orders.csv", ResourceNodeType.FILE, 2_048L, now.minusHours(1));
    ResourceNode recent = resource(3L, "config.json", ResourceNodeType.FILE, 1_024L, now.minusDays(2));
    recent.setUpdateTime(null);
    recent.setCreateTime(now.minusDays(2));
    ResourceNode old = resource(4L, "archive.zip", ResourceNodeType.FILE, 4_096L, now.minusDays(8));

    HomeResourceCenterReader.OverviewResponse response =
        reader.aggregate(
            List.of(
                new ResourceTreeNode(folder, List.of(new ResourceTreeNode(newest, List.of()))),
                new ResourceTreeNode(recent, List.of()),
                new ResourceTreeNode(old, List.of())),
            now);

    assertThat(response.available()).isTrue();
    assertThat(response.fileCount()).isEqualTo(3L);
    assertThat(response.folderCount()).isEqualTo(1L);
    assertThat(response.totalBytes()).isEqualTo(7_168L);
    assertThat(response.recentFiles())
        .extracting(HomeResourceCenterReader.RecentFile::name)
        .containsExactly("orders.csv", "config.json");
    assertThat(response.recentFiles().get(0).fileSize()).isEqualTo(2_048L);
  }

  @Test
  void shouldLimitRecentFilesToThreeNewestItems() {
    @SuppressWarnings("unchecked")
    ObjectProvider<ResourceTreeReader> provider = mock(ObjectProvider.class);
    HomeResourceCenterReader reader = new HomeResourceCenterReader(provider);
    LocalDateTime now = LocalDateTime.of(2026, 9, 9, 9, 0);

    List<ResourceTreeNode> resources =
        List.of(
            new ResourceTreeNode(resource(1L, "one.txt", ResourceNodeType.FILE, 1L, now.minusHours(1)), List.of()),
            new ResourceTreeNode(resource(2L, "two.txt", ResourceNodeType.FILE, 1L, now.minusHours(2)), List.of()),
            new ResourceTreeNode(resource(3L, "three.txt", ResourceNodeType.FILE, 1L, now.minusHours(3)), List.of()),
            new ResourceTreeNode(resource(4L, "four.txt", ResourceNodeType.FILE, 1L, now.minusHours(4)), List.of()));

    HomeResourceCenterReader.OverviewResponse response = reader.aggregate(resources, now);

    assertThat(response.recentFiles())
        .extracting(HomeResourceCenterReader.RecentFile::name)
        .containsExactly("one.txt", "two.txt", "three.txt");
  }

  @Test
  void shouldExposeUnavailableStateWhenResourceModuleIsDisabled() {
    @SuppressWarnings("unchecked")
    ObjectProvider<ResourceTreeReader> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);

    HomeResourceCenterReader.OverviewResponse response =
        new HomeResourceCenterReader(provider).overview();

    assertThat(response.available()).isFalse();
    assertThat(response.fileCount()).isNull();
    assertThat(response.folderCount()).isNull();
    assertThat(response.totalBytes()).isNull();
    assertThat(response.recentFiles()).isEmpty();
  }

  @Test
  void shouldExposeUnavailableStateWhenResourceQueryFails() {
    ResourceTreeReader treeReader = mock(ResourceTreeReader.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ResourceTreeReader> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(treeReader);
    when(treeReader.tree()).thenThrow(new IllegalStateException("resource unavailable"));

    HomeResourceCenterReader.OverviewResponse response =
        new HomeResourceCenterReader(provider).overview();

    assertThat(response.available()).isFalse();
    assertThat(response.fileCount()).isNull();
    assertThat(response.recentFiles()).isEmpty();
  }

  private ResourceNode resource(
      long id,
      String name,
      ResourceNodeType nodeType,
      Long fileSize,
      LocalDateTime updatedAt) {
    ResourceNode resource = new ResourceNode();
    resource.setId(id);
    resource.setParentId(0L);
    resource.setName(name);
    resource.setFullPath("/" + name);
    resource.setNodeType(nodeType);
    resource.setSuffix(nodeType == ResourceNodeType.FILE ? suffix(name) : null);
    resource.setFileSize(fileSize);
    resource.setCreateTime(updatedAt.minusHours(1));
    resource.setUpdateTime(updatedAt);
    return resource;
  }

  private String suffix(String name) {
    int separator = name.lastIndexOf('.');
    return separator < 0 ? null : name.substring(separator + 1);
  }
}
