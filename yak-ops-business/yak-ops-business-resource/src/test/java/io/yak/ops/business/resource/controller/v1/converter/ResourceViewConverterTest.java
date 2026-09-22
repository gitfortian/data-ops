package io.yak.ops.business.resource.controller.v1.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.resource.domain.ResourceContent;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceStoragePlugin;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import io.yak.ops.common.enums.resource.ResourceStorageType;
import org.junit.jupiter.api.Test;

class ResourceViewConverterTest {
  private final ResourceViewConverter converter = new ResourceViewConverter();

  @Test
  void mapsNodeContentAndStoragePluginWithoutPersistenceTypes() {
    ResourceNode node = new ResourceNode();
    node.setId(11L);
    node.setParentId(0L);
    node.setName("README.md");
    node.setFullPath("/README.md");
    node.setNodeType(ResourceNodeType.FILE);
    node.setStorageType(ResourceStorageType.LOCAL);
    node.setFileSize(128L);

    var nodeView = converter.node(node);
    assertThat(nodeView.getId()).isEqualTo(11L);
    assertThat(nodeView.getFullPath()).isEqualTo("/README.md");
    assertThat(nodeView.getChildren()).isEmpty();

    var contentView = converter.content(new ResourceContent(11L, "/README.md", "hello", 0, 1, false));
    assertThat(contentView.getResourceId()).isEqualTo(11L);
    assertThat(contentView.getContent()).isEqualTo("hello");
    assertThat(contentView.isHasMore()).isFalse();

    var pluginView = converter.storagePlugin(new ResourceStoragePlugin(ResourceStorageType.LOCAL, "本地存储", true));
    assertThat(pluginView.getType()).isEqualTo(ResourceStorageType.LOCAL);
    assertThat(pluginView.getName()).isEqualTo("本地存储");
    assertThat(pluginView.isActive()).isTrue();
  }
}
