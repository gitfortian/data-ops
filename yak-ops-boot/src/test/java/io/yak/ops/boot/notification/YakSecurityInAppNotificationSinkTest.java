package io.yak.ops.boot.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.security.common.dto.message.MessageDTO;
import io.yak.framework.security.common.enums.project.ProjectUserCode;
import io.yak.framework.security.notification.NotificationPublisher;
import io.yak.framework.security.service.UserProjectService;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationPolicy;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class YakSecurityInAppNotificationSinkTest {

  @Test
  @SuppressWarnings("unchecked")
  void sendsOneProjectOwnedMessageToEachDistinctOwner() {
    UserProjectService projects = mock(UserProjectService.class);
    NotificationPublisher publisher = mock(NotificationPublisher.class);
    ObjectProvider<UserProjectService> projectProvider = mock(ObjectProvider.class);
    ObjectProvider<NotificationPublisher> publisherProvider = mock(ObjectProvider.class);
    when(projectProvider.getIfAvailable()).thenReturn(projects);
    when(publisherProvider.getIfAvailable()).thenReturn(publisher);
    when(projects.getUserIdListByProjectId(7L, ProjectUserCode.OWNER))
        .thenReturn(List.of(11L, 12L, 11L));

    YakSecurityInAppNotificationSink sink =
        new YakSecurityInAppNotificationSink(projectProvider, publisherProvider);
    sink.deliver(intent(), NotificationPolicy.projectOwnersInApp());

    ArgumentCaptor<List<MessageDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(publisher).publishAll(captor.capture());
    assertThat(captor.getValue()).hasSize(2);
    assertThat(captor.getValue()).extracting(MessageDTO::getUserId)
        .containsExactly(11L, 12L);
    assertThat(captor.getValue()).allSatisfy(message -> {
      assertThat(message.getProjectId()).isEqualTo(7L);
      assertThat(message.getType()).isEqualTo("TASK");
      assertThat(message.getLevel()).isEqualTo("ERROR");
      assertThat(message.getSourceType()).isEqualTo("OFFLINE_SYNC_EXECUTION");
      assertThat(message.getActionPath()).isEqualTo("/sync/batch-link-up/10/detail");
    });
  }

  @Test
  @SuppressWarnings("unchecked")
  void explicitRecipientsDoNotRequireProjectOwnerLookup() {
    UserProjectService projects = mock(UserProjectService.class);
    NotificationPublisher publisher = mock(NotificationPublisher.class);
    ObjectProvider<UserProjectService> projectProvider = mock(ObjectProvider.class);
    ObjectProvider<NotificationPublisher> publisherProvider = mock(ObjectProvider.class);
    when(projectProvider.getIfAvailable()).thenReturn(projects);
    when(publisherProvider.getIfAvailable()).thenReturn(publisher);
    NotificationPolicy policy = new NotificationPolicy(
        true,
        NotificationPolicy.RecipientStrategy.EXPLICIT_USERS,
        List.of(21L, 22L),
        Set.of(NotificationPolicy.Destination.IN_APP),
        List.of());

    YakSecurityInAppNotificationSink sink =
        new YakSecurityInAppNotificationSink(projectProvider, publisherProvider);
    sink.deliver(intent(), policy);

    verify(projects, never()).getUserIdListByProjectId(anyLong(), any());
    ArgumentCaptor<List<MessageDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(publisher).publishAll(captor.capture());
    assertThat(captor.getValue()).extracting(MessageDTO::getUserId)
        .containsExactly(21L, 22L);
  }

  private NotificationIntent intent() {
    return new NotificationIntent(
        7L,
        NotificationIntent.Type.TASK,
        NotificationIntent.Level.ERROR,
        "离线同步任务执行失败",
        "订单同步",
        "engine down",
        "OFFLINE_SYNC_EXECUTION",
        "99",
        "/sync/batch-link-up/10/detail");
  }
}
