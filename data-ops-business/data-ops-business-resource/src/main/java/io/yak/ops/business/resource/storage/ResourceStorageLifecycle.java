package io.yak.ops.business.resource.storage;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.common.enums.resource.ResourceStorageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Storage compensation and post-commit lifecycle actions. */
@Slf4j
@Component
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
public class ResourceStorageLifecycle {

  private final ResourceStorageGateway storage;

  public void cleanupCreated(
      ResourceStorageType type,
      String storagePath,
      boolean recursive) {
    try {
      storage.delete(type, storagePath, recursive);
    } catch (RuntimeException cleanupException) {
      log.warn(
          "Failed to cleanup resource storage object after persistence failure: {}",
          storagePath,
          cleanupException);
    }
  }

  public void rollbackMove(
      ResourceStorageType type,
      String currentPath,
      String previousPath) {
    try {
      storage.move(type, currentPath, previousPath, false);
    } catch (RuntimeException rollbackException) {
      log.error(
          "Failed to rollback resource storage move: {} -> {}",
          currentPath,
          previousPath,
          rollbackException);
    }
  }

  public void deleteAfterCommit(
      ResourceStorageType type,
      String storagePath,
      boolean recursive) {
    afterCommit(
        () -> {
          try {
            storage.delete(type, storagePath, recursive);
          } catch (RuntimeException exception) {
            log.error(
                "Failed to remove resource object after metadata deletion: {}",
                storagePath,
                exception);
          }
        });
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
}
