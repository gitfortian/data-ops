package io.yak.ops.business.resource.controller.v1.converter;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.domain.ResourceContent;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceStoragePlugin;
import io.yak.ops.business.resource.domain.ResourceTreeNode;
import io.yak.ops.common.bean.vo.resource.ResourceContentVO;
import io.yak.ops.common.bean.vo.resource.ResourceStoragePluginVO;
import io.yak.ops.common.bean.vo.resource.ResourceVO;
import org.springframework.stereotype.Component;

/** Resource Domain to HTTP VO conversion. */
@Component
@ConditionalOnResourceEnabled
public class ResourceViewConverter {

  public ResourceVO node(ResourceNode resource) {
    if (resource == null) return null;
    return ResourceVO.builder()
        .id(resource.getId()).parentId(resource.getParentId()).name(resource.getName())
        .fullPath(resource.getFullPath()).nodeType(resource.getNodeType()).storageType(resource.getStorageType())
        .contentType(resource.getContentType()).suffix(resource.getSuffix()).fileSize(resource.getFileSize())
        .checksum(resource.getChecksum()).description(resource.getDescription()).version(resource.getVersion())
        .gitSyncStatus(resource.getGitSyncStatus()).createTime(resource.getCreateTime()).updateTime(resource.getUpdateTime())
        .build();
  }

  public ResourceVO tree(ResourceTreeNode tree) {
    ResourceVO result = node(tree.resource());
    for (ResourceTreeNode child : tree.children()) result.getChildren().add(tree(child));
    return result;
  }

  public ResourceContentVO content(ResourceContent content) {
    if (content == null) return null;
    return ResourceContentVO.builder().resourceId(content.resourceId()).fullPath(content.fullPath())
        .content(content.content()).skipLineNum(content.skipLineNum()).lineCount(content.lineCount())
        .hasMore(content.hasMore()).build();
  }

  public ResourceStoragePluginVO storagePlugin(ResourceStoragePlugin plugin) {
    if (plugin == null) return null;
    return ResourceStoragePluginVO.builder().type(plugin.type()).name(plugin.name()).active(plugin.active()).build();
  }
}
