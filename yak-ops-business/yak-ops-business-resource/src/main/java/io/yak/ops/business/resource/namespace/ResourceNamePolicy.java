package io.yak.ops.business.resource.namespace;

import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import org.springframework.stereotype.Component;

/** Namespace naming policy. */
@Component
public class ResourceNamePolicy {

  public String normalize(String value) {
    if (value == null || value.isBlank()) {
      throw new ResourceException(ResourceErrorCode.INVALID_NAME, "名称不能为空");
    }
    String name = value.trim();
    if (name.length() > 255) {
      throw new ResourceException(ResourceErrorCode.INVALID_NAME, "名称不能超过 255 个字符");
    }
    if (".".equals(name)
        || "..".equals(name)
        || name.indexOf('/') >= 0
        || name.indexOf('\\') >= 0
        || name.indexOf('\0') >= 0) {
      throw new ResourceException(ResourceErrorCode.INVALID_NAME, name);
    }
    return name;
  }

  public String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
