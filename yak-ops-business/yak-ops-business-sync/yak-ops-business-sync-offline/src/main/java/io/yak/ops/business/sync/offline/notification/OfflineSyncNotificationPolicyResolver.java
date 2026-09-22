package io.yak.ops.business.sync.offline.notification;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationPolicy;
import io.yak.ops.core.notification.NotificationPolicyResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Overrides the global notification fallback with the owning Offline Sync task policy. */
@Component
@ConditionalOnOfflineSyncEnabled
public class OfflineSyncNotificationPolicyResolver implements NotificationPolicyResolver {

  static final String SOURCE_TYPE = "OFFLINE_SYNC_EXECUTION";
  static final int ORDER = 100;

  private final OfflineNotificationPolicyReader reader;
  private final OfflineNotificationPolicyCodec codec;

  public OfflineSyncNotificationPolicyResolver(
      OfflineNotificationPolicyReader reader,
      OfflineNotificationPolicyCodec codec) {
    this.reader = reader;
    this.codec = codec;
  }

  @Override
  public boolean supports(NotificationIntent intent) {
    return intent != null && SOURCE_TYPE.equalsIgnoreCase(intent.sourceType());
  }

  @Override
  public NotificationPolicy resolve(NotificationIntent intent) {
    if (intent == null || !StringUtils.hasText(intent.sourceId())) {
      throw new IllegalArgumentException("离线同步通知缺少 executionId");
    }
    long executionId;
    try {
      executionId = Long.parseLong(intent.sourceId().trim());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("离线同步通知 executionId 不合法", exception);
    }
    if (executionId <= 0L) {
      throw new IllegalArgumentException("离线同步通知 executionId 必须大于 0");
    }

    OfflineNotificationPolicyReader.Snapshot snapshot = reader
        .find(intent.projectId(), executionId)
        .orElseThrow(() -> new IllegalStateException(
            "无法解析离线同步通知策略：projectId=" + intent.projectId()
                + ", executionId=" + executionId));
    return codec.decodePolicy(snapshot.notificationConfigJson());
  }

  @Override
  public int order() {
    return ORDER;
  }
}
