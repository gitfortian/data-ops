package io.yak.ops.business.resource.sync;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.spi.resource.ResourceFileSyncAction;
import io.yak.ops.spi.resource.ResourceFileSyncContext;
import io.yak.ops.spi.resource.ResourceFileSyncProvider;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Propagates committed Resource changes to optional external sync providers. */
@Slf4j
@Component
@ConditionalOnResourceEnabled
public class ResourceChangeDispatcher {

  private final List<ResourceFileSyncProvider> providers;

  public ResourceChangeDispatcher(List<ResourceFileSyncProvider> providers) {
    this.providers = providers;
  }

  public void dispatchAfterCommit(
      ResourceNode resource,
      ResourceFileSyncAction action,
      String oldFullPath) {
    if (resource == null || action == null || providers.isEmpty()) {
      return;
    }
    ResourceFileSyncContext context =
        ResourceFileSyncContext.builder()
            .resourceId(resource.getId())
            .action(action)
            .nodeType(resource.getNodeType())
            .storageType(resource.getStorageType())
            .oldFullPath(oldFullPath)
            .fullPath(resource.getFullPath())
            .storagePath(resource.getStoragePath())
            .version(resource.getVersion())
            .build();
    afterCommit(() -> dispatch(context));
  }

  private void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }

  private void dispatch(ResourceFileSyncContext context) {
    for (ResourceFileSyncProvider provider : providers) {
      try {
        provider.synchronize(context);
      } catch (RuntimeException exception) {
        log.warn(
            "Resource sync provider {} failed for resource {}",
            provider.type(),
            context.getResourceId(),
            exception);
      }
    }
  }
}
