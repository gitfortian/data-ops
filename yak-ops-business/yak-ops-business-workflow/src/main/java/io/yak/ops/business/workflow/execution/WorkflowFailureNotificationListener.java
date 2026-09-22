package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.workflow.domain.WorkflowExecutionTerminalEvent;
import io.yak.ops.business.workflow.execution.WorkflowExecutionNotificationReader.Snapshot;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Publishes notification intents for failed or timed-out Workflow executions. */
@Component
public class WorkflowFailureNotificationListener {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(WorkflowFailureNotificationListener.class);

  private final WorkflowExecutionNotificationReader executionReader;
  private final ObjectProvider<NotificationRouter> notificationRouters;

  public WorkflowFailureNotificationListener(
      WorkflowExecutionNotificationReader executionReader,
      ObjectProvider<NotificationRouter> notificationRouters) {
    this.executionReader = executionReader;
    this.notificationRouters = notificationRouters;
  }

  @EventListener
  public void onTerminal(WorkflowExecutionTerminalEvent event) {
    if (event == null || !notifiable(event.executionStatus())) return;
    NotificationRouter router = notificationRouters.getIfAvailable();
    if (router == null) return;

    try {
      Snapshot execution = executionReader.find(event.executionId()).orElse(null);
      if (execution == null) return;

      String workflowName = StringUtils.hasText(execution.workflowName())
          ? execution.workflowName().trim()
          : "工作流 " + event.executionId();
      boolean timedOut = "TIMED_OUT".equalsIgnoreCase(event.executionStatus());
      String title = timedOut ? "工作流执行超时" : "工作流执行失败";
      String content = StringUtils.hasText(execution.errorMessage())
          ? execution.errorMessage().trim()
          : timedOut
              ? "工作流执行已超时，请查看实例详情。"
              : "工作流执行失败，请查看实例详情。";

      router.publish(
          new NotificationIntent(
              execution.projectId(),
              NotificationIntent.Type.TASK,
              NotificationIntent.Level.ERROR,
              title,
              workflowName,
              content,
              "WORKFLOW_EXECUTION",
              event.executionId(),
              "/workflow/instances/" + event.executionId()));
    } catch (RuntimeException exception) {
      LOGGER.error(
          "Failed to publish Workflow failure notification intent: execution={}, status={}",
          event.executionId(),
          event.executionStatus(),
          exception);
    }
  }

  private boolean notifiable(String status) {
    return "FAILED".equalsIgnoreCase(status) || "TIMED_OUT".equalsIgnoreCase(status);
  }
}
