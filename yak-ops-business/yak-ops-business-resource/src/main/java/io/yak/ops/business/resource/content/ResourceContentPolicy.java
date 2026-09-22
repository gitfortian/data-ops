package io.yak.ops.business.resource.content;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.config.ResourceProperties;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourcePath;
import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** File-size, editable-content and content-type policies. */
@Component
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
public class ResourceContentPolicy {

  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

  private final ResourceProperties properties;

  public void ensureFileSize(long size) {
    if (size < 0L || size > properties.getMaxFileSize()) {
      throw new ResourceException(
          ResourceErrorCode.FILE_TOO_LARGE,
          "最大允许 " + properties.getMaxFileSize() + " 字节");
    }
  }

  public void ensureEditable(ResourceNode resource) {
    ensureEditableName(resource.getName());
  }

  public void ensureReadableContent(ResourceNode resource) {
    ensureEditable(resource);
    if (resource.getFileSize() != null
        && resource.getFileSize() > properties.getEditableMaxBytes()) {
      throw new ResourceException(
          ResourceErrorCode.CONTENT_NOT_EDITABLE,
          "文件超过在线查看大小限制");
    }
  }

  public void ensureEditableName(String name) {
    String suffix = ResourcePath.suffix(name);
    if (suffix == null
        || properties.getEditableSuffixes() == null
        || !properties.getEditableSuffixes().contains(suffix.toLowerCase(Locale.ROOT))) {
      throw new ResourceException(ResourceErrorCode.CONTENT_NOT_EDITABLE, name);
    }
  }

  public void ensureEditableContentSize(long size) {
    if (size > properties.getEditableMaxBytes()) {
      throw new ResourceException(
          ResourceErrorCode.CONTENT_NOT_EDITABLE,
          "在线编辑内容不能超过 " + properties.getEditableMaxBytes() + " 字节");
    }
  }

  public String contentType(String value) {
    return value == null || value.isBlank() ? DEFAULT_CONTENT_TYPE : value.trim();
  }
}
