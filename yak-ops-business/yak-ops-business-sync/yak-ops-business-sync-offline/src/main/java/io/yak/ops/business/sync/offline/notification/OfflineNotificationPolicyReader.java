package io.yak.ops.business.sync.offline.notification;

import java.util.Optional;

/** Read-only port used by notification routing after the business transaction commits. */
public interface OfflineNotificationPolicyReader {

  Optional<Snapshot> find(long projectId, long executionId);

  record Snapshot(long definitionId, String notificationConfigJson) {}
}
