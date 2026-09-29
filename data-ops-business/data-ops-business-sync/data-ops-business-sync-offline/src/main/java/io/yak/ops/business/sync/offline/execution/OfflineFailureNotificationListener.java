package io.yak.ops.business.sync.offline.execution;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionFinalFailureEvent;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Converts final Offline Sync failures into user-facing notification intents. */
@Component
@ConditionalOnOfflineSyncEnabled
public class OfflineFailureNotificationListener {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OfflineFailureNotificationListener.class);

  private final OfflineJobDefinitionRepository definitionRepository;
  private final ObjectProvider<NotificationRouter> notificationRouters;

  public OfflineFailureNotificationListener(
      OfflineJobDefinitionRepository definitionRepository,
      ObjectProvider<NotificationRouter> notificationRouters) {
    this.definitionRepository = definitionRepository;
    this.notificationRouters = notificationRouters;
  }

  @EventListener
  public void onFinalFailure(OfflineExecutionFinalFailureEvent event) {
    NotificationRouter router = notificationRouters.getIfAvailable();
    if (router == null || event == null || event.jobDefinitionId() == null) return;

    try {
      OfflineJobDefinition definition =
          definitionRepository.findById(event.jobDefinitionId()).orElse(null);
      if (definition == null) return;

      String taskName = StringUtils.hasText(definition.getJobName())
          ? definition.getJobName().trim()
          : "离线同步任务 #" + definition.getId();
      String relation = relation(definition);
      String summary = relation == null ? taskName : taskName + " · " + relation;
      String content = StringUtils.hasText(event.errorMessage())
          ? event.errorMessage().trim()
          : "离线同步执行失败，且当前重试策略已没有后续重试，请查看任务详情。";

      router.publish(
          new NotificationIntent(
              definition.requireProjectId(),
              NotificationIntent.Type.TASK,
              NotificationIntent.Level.ERROR,
              "离线同步任务执行失败",
              summary,
              content,
              "OFFLINE_SYNC_EXECUTION",
              String.valueOf(event.executionId()),
              "/sync/batch-link-up/" + definition.getId() + "/detail"));
    } catch (RuntimeException exception) {
      LOGGER.error(
          "Failed to publish Offline Sync failure notification intent: execution={}, definition={}",
          event.executionId(),
          event.jobDefinitionId(),
          exception);
    }
  }

  private String relation(OfflineJobDefinition definition) {
    String source = StringUtils.hasText(definition.getSourceTable())
        ? definition.getSourceTable().trim()
        : null;
    String sink = StringUtils.hasText(definition.getSinkTable())
        ? definition.getSinkTable().trim()
        : null;
    if (source == null && sink == null) return null;
    if (source == null) return sink;
    if (sink == null) return source;
    return source + " → " + sink;
  }
}
