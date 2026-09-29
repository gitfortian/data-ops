package io.yak.ops.boot.notification;

import io.yak.framework.security.common.dto.message.MessageDTO;
import io.yak.framework.security.common.enums.project.ProjectUserCode;
import io.yak.framework.security.notification.NotificationPublisher;
import io.yak.framework.security.service.UserProjectService;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationPolicy;
import io.yak.ops.core.notification.NotificationSink;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** IN_APP notification sink backed by Yak Security's Message Center. */
@Component
public class YakSecurityInAppNotificationSink implements NotificationSink {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(YakSecurityInAppNotificationSink.class);

  private final ObjectProvider<UserProjectService> userProjectServices;
  private final ObjectProvider<NotificationPublisher> notificationPublishers;

  public YakSecurityInAppNotificationSink(
      ObjectProvider<UserProjectService> userProjectServices,
      ObjectProvider<NotificationPublisher> notificationPublishers) {
    this.userProjectServices = userProjectServices;
    this.notificationPublishers = notificationPublishers;
  }

  @Override
  public NotificationPolicy.Destination destination() {
    return NotificationPolicy.Destination.IN_APP;
  }

  @Override
  public void deliver(NotificationIntent intent, NotificationPolicy policy) {
    Objects.requireNonNull(intent, "intent");
    Objects.requireNonNull(policy, "policy");

    NotificationPublisher notificationPublisher = notificationPublishers.getIfAvailable();
    if (notificationPublisher == null) {
      LOGGER.debug(
          "IN_APP notification skipped because Yak Security publisher is unavailable: sourceType={}, sourceId={}",
          intent.sourceType(),
          intent.sourceId());
      return;
    }

    List<Long> recipientIds = resolveRecipients(intent, policy);
    if (recipientIds.isEmpty()) {
      LOGGER.debug(
          "IN_APP notification skipped because no recipient was resolved: projectId={}, sourceType={}, sourceId={}",
          intent.projectId(),
          intent.sourceType(),
          intent.sourceId());
      return;
    }

    notificationPublisher.publishAll(
        recipientIds.stream().map(userId -> toMessage(intent, userId)).toList());
  }

  private List<Long> resolveRecipients(
      NotificationIntent intent,
      NotificationPolicy policy) {
    if (policy.recipientStrategy() == NotificationPolicy.RecipientStrategy.EXPLICIT_USERS) {
      return policy.recipientUserIds();
    }

    UserProjectService userProjectService = userProjectServices.getIfAvailable();
    if (userProjectService == null) return List.of();
    List<Long> resolvedOwnerIds =
        userProjectService.getUserIdListByProjectId(intent.projectId(), ProjectUserCode.OWNER);
    if (resolvedOwnerIds == null) return List.of();
    return resolvedOwnerIds.stream()
        .filter(Objects::nonNull)
        .filter(userId -> userId > 0L)
        .distinct()
        .toList();
  }

  private MessageDTO toMessage(NotificationIntent intent, Long userId) {
    MessageDTO message = new MessageDTO();
    message.setUserId(userId);
    message.setProjectId(intent.projectId());
    message.setType(intent.type().name());
    message.setLevel(intent.level().name());
    message.setTitle(intent.title());
    message.setSummary(intent.summary());
    message.setContent(intent.content());
    message.setSourceType(intent.sourceType());
    message.setSourceId(intent.sourceId());
    message.setActionPath(intent.actionPath());
    return message;
  }
}
