package io.yak.ops.business.alert.notification;

import io.yak.ops.business.alert.domain.AlertChannelDefinition;
import io.yak.ops.business.alert.repository.AlertChannelRepository;
import io.yak.ops.business.alert.service.AlertService;
import io.yak.ops.common.bean.dto.alert.AlertNotifyDTO;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationPolicy;
import io.yak.ops.core.notification.NotificationSink;
import io.yak.ops.plugin.alert.api.AlertResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Routes Notification Router ALERT destinations through the configured Alert plugin system. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotificationSink implements NotificationSink {

  private final AlertChannelRepository channelRepository;
  private final AlertService alertService;

  @Override
  public NotificationPolicy.Destination destination() {
    return NotificationPolicy.Destination.ALERT;
  }

  @Override
  public void deliver(NotificationIntent intent, NotificationPolicy policy) {
    for (Long channelId : policy.alertChannelIds()) {
      deliverChannel(intent, channelId);
    }
  }

  private void deliverChannel(NotificationIntent intent, Long channelId) {
    try {
      AlertChannelDefinition channel = channelRepository.findById(channelId).orElse(null);
      if (channel == null) {
        log.warn(
            "Notification ALERT channel no longer exists: channelId={}, projectId={}, sourceType={}, sourceId={}",
            channelId,
            intent.projectId(),
            intent.sourceType(),
            intent.sourceId());
        return;
      }

      AlertNotifyDTO dto = new AlertNotifyDTO();
      dto.setChannelType(channel.getChannelType());
      dto.setTitle(intent.title());
      dto.setContent(content(intent));
      dto.setLevel(level(intent.level()));

      AlertResult result = alertService.notify(dto);
      if (result == null || !result.success()) {
        log.warn(
            "Notification ALERT delivery failed: channelId={}, channelType={}, projectId={}, sourceType={}, sourceId={}, error={}",
            channelId,
            channel.getChannelType(),
            intent.projectId(),
            intent.sourceType(),
            intent.sourceId(),
            result == null ? "null result" : result.errorMessage());
      }
    } catch (RuntimeException exception) {
      // One broken external channel must not suppress the remaining selected channels.
      log.error(
          "Notification ALERT delivery raised an exception: channelId={}, projectId={}, sourceType={}, sourceId={}",
          channelId,
          intent.projectId(),
          intent.sourceType(),
          intent.sourceId(),
          exception);
    }
  }

  private String content(NotificationIntent intent) {
    String summary = intent.summary();
    String detail = intent.content();
    String body;
    if (summary != null && detail != null && !summary.equals(detail)) {
      body = summary + "\n\n" + detail;
    } else if (detail != null) {
      body = detail;
    } else if (summary != null) {
      body = summary;
    } else {
      body = intent.title();
    }

    if (intent.actionPath() != null) {
      return body + "\n\n查看详情：" + intent.actionPath();
    }
    return body;
  }

  private String level(NotificationIntent.Level level) {
    return switch (level) {
      case WARNING -> "WARN";
      case ERROR -> "ERROR";
      case INFO, SUCCESS -> "INFO";
    };
  }
}
