package io.yak.ops.boot.notification;

import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationPolicy;
import io.yak.ops.core.notification.NotificationPolicyResolver;
import org.springframework.stereotype.Component;

/** Lowest-priority compatibility policy used when no business-specific policy resolver matches. */
@Component
public class DefaultNotificationPolicyResolver implements NotificationPolicyResolver {

  @Override
  public boolean supports(NotificationIntent intent) {
    return true;
  }

  @Override
  public NotificationPolicy resolve(NotificationIntent intent) {
    return NotificationPolicy.projectOwnersInApp();
  }

  @Override
  public int order() {
    return Integer.MAX_VALUE;
  }
}
