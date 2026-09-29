package io.yak.ops.business.resource.storage;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.domain.ResourceStoragePlugin;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Read side for installed storage plugins. */
@Component
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
public class ResourceStorageReader {

  private final ResourceStorageRegistry registry;

  public List<ResourceStoragePlugin> list() {
    return registry.list();
  }
}
