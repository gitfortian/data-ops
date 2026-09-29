package io.yak.ops.business.alert.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.alert.domain.AlertChannelDefinition;
import io.yak.ops.business.alert.repository.AlertChannelRepository;
import io.yak.ops.business.alert.service.AlertService;
import io.yak.ops.common.bean.dto.alert.AlertNotifyDTO;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationPolicy;
import io.yak.ops.plugin.alert.api.AlertResult;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlertNotificationSinkTest {

  @Test
  void sendsEachSelectedChannelThroughAlertService() {
    AlertChannelRepository repository = mock(AlertChannelRepository.class);
    AlertService alertService = mock(AlertService.class);
    when(repository.findById(7L)).thenReturn(Optional.of(channel(7L, "DINGTALK")));
    when(repository.findById(8L)).thenReturn(Optional.of(channel(8L, "CUSTOM")));
    when(alertService.notify(any(AlertNotifyDTO.class))).thenReturn(AlertResult.ok());

    AlertNotificationSink sink = new AlertNotificationSink(repository, alertService);
    sink.deliver(intent(NotificationIntent.Level.WARNING), alertPolicy(7L, 8L));

    ArgumentCaptor<AlertNotifyDTO> captor = ArgumentCaptor.forClass(AlertNotifyDTO.class);
    verify(alertService, times(2)).notify(captor.capture());

    assertThat(captor.getAllValues())
        .extracting(AlertNotifyDTO::getChannelType)
        .containsExactly("DINGTALK", "CUSTOM");
    assertThat(captor.getAllValues())
        .extracting(AlertNotifyDTO::getLevel)
        .containsOnly("WARN");
    assertThat(captor.getAllValues())
        .extracting(AlertNotifyDTO::getTitle)
        .containsOnly("离线同步任务执行失败");
    assertThat(captor.getAllValues().get(0).getContent())
        .contains("orders -> ods_orders")
        .contains("连接目标库超时")
        .contains("/sync/batch-link-up/10/detail");
  }

  @Test
  void missingChannelDoesNotSuppressRemainingChannels() {
    AlertChannelRepository repository = mock(AlertChannelRepository.class);
    AlertService alertService = mock(AlertService.class);
    when(repository.findById(7L)).thenReturn(Optional.empty());
    when(repository.findById(8L)).thenReturn(Optional.of(channel(8L, "DINGTALK")));
    when(alertService.notify(any(AlertNotifyDTO.class))).thenReturn(AlertResult.ok());

    new AlertNotificationSink(repository, alertService)
        .deliver(intent(NotificationIntent.Level.ERROR), alertPolicy(7L, 8L));

    verify(alertService, times(1)).notify(any(AlertNotifyDTO.class));
  }

  @Test
  void failedChannelResultDoesNotThrowOrStopNextChannel() {
    AlertChannelRepository repository = mock(AlertChannelRepository.class);
    AlertService alertService = mock(AlertService.class);
    when(repository.findById(7L)).thenReturn(Optional.of(channel(7L, "DINGTALK")));
    when(repository.findById(8L)).thenReturn(Optional.of(channel(8L, "CUSTOM")));
    when(alertService.notify(any(AlertNotifyDTO.class)))
        .thenReturn(AlertResult.fail("network error"))
        .thenReturn(AlertResult.ok());

    new AlertNotificationSink(repository, alertService)
        .deliver(intent(NotificationIntent.Level.ERROR), alertPolicy(7L, 8L));

    verify(alertService, times(2)).notify(any(AlertNotifyDTO.class));
  }

  private NotificationPolicy alertPolicy(Long... ids) {
    return new NotificationPolicy(
        true,
        NotificationPolicy.RecipientStrategy.PROJECT_OWNER,
        List.of(),
        Set.of(NotificationPolicy.Destination.ALERT),
        Arrays.asList(ids));
  }

  private NotificationIntent intent(NotificationIntent.Level level) {
    return new NotificationIntent(
        3L,
        NotificationIntent.Type.TASK,
        level,
        "离线同步任务执行失败",
        "orders -> ods_orders",
        "连接目标库超时",
        "OFFLINE_SYNC_EXECUTION",
        "1001",
        "/sync/batch-link-up/10/detail");
  }

  private AlertChannelDefinition channel(long id, String type) {
    AlertChannelDefinition channel = new AlertChannelDefinition();
    channel.setId(id);
    channel.setChannelType(type);
    channel.setEnabled(true);
    return channel;
  }
}
