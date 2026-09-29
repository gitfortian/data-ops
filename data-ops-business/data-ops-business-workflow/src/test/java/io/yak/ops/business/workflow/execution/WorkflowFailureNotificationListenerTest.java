package io.yak.ops.business.workflow.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.domain.WorkflowExecutionTerminalEvent;
import io.yak.ops.business.workflow.execution.WorkflowExecutionNotificationReader.Snapshot;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationRouter;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class WorkflowFailureNotificationListenerTest {

  @Test
  @SuppressWarnings("unchecked")
  void failedExecutionPublishesProjectOwnedTaskIntent() {
    WorkflowExecutionNotificationReader executions = mock(WorkflowExecutionNotificationReader.class);
    NotificationRouter router = mock(NotificationRouter.class);
    ObjectProvider<NotificationRouter> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(router);
    when(executions.find("exec-1")).thenReturn(Optional.of(
        new Snapshot(7L, "exec-1", "每日订单加工", "FAILED", "SQL task failed")));

    WorkflowFailureNotificationListener listener =
        new WorkflowFailureNotificationListener(executions, provider);
    listener.onTerminal(new WorkflowExecutionTerminalEvent("exec-1", "FAILED", Instant.now()));

    ArgumentCaptor<NotificationIntent> captor =
        ArgumentCaptor.forClass(NotificationIntent.class);
    verify(router).publish(captor.capture());
    NotificationIntent intent = captor.getValue();
    assertThat(intent.projectId()).isEqualTo(7L);
    assertThat(intent.type()).isEqualTo(NotificationIntent.Type.TASK);
    assertThat(intent.level()).isEqualTo(NotificationIntent.Level.ERROR);
    assertThat(intent.title()).isEqualTo("工作流执行失败");
    assertThat(intent.summary()).isEqualTo("每日订单加工");
    assertThat(intent.content()).isEqualTo("SQL task failed");
    assertThat(intent.actionPath()).isEqualTo("/workflow/instances/exec-1");
  }

  @Test
  @SuppressWarnings("unchecked")
  void successfulExecutionDoesNotPublishNotification() {
    WorkflowExecutionNotificationReader executions = mock(WorkflowExecutionNotificationReader.class);
    NotificationRouter router = mock(NotificationRouter.class);
    ObjectProvider<NotificationRouter> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(router);

    WorkflowFailureNotificationListener listener =
        new WorkflowFailureNotificationListener(executions, provider);
    listener.onTerminal(new WorkflowExecutionTerminalEvent("exec-1", "SUCCESS", Instant.now()));

    verify(executions, never()).find(any());
    verify(router, never()).publish(any());
  }
}
