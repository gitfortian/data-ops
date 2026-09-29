package io.yak.ops.business.sync.offline.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobNotificationDTO;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationPolicy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfflineSyncNotificationPolicyResolverTest {

  @Test
  void resolvesExecutionToConfiguredExplicitRecipients() {
    OfflineNotificationPolicyReader reader = mock(OfflineNotificationPolicyReader.class);
    OfflineNotificationPolicyCodec codec =
        new OfflineNotificationPolicyCodec(new ObjectMapper());
    OfflineJobNotificationDTO config = new OfflineJobNotificationDTO();
    config.setRecipientType("EXPLICIT_USERS");
    config.setRecipientUserIds(List.of(31L, 32L));
    when(reader.find(7L, 99L)).thenReturn(Optional.of(
        new OfflineNotificationPolicyReader.Snapshot(10L, codec.encode(config))));

    OfflineSyncNotificationPolicyResolver resolver =
        new OfflineSyncNotificationPolicyResolver(reader, codec);
    NotificationPolicy policy = resolver.resolve(intent());

    verify(reader).find(7L, 99L);
    assertThat(resolver.supports(intent())).isTrue();
    assertThat(resolver.order()).isEqualTo(100);
    assertThat(policy.recipientStrategy())
        .isEqualTo(NotificationPolicy.RecipientStrategy.EXPLICIT_USERS);
    assertThat(policy.recipientUserIds()).containsExactly(31L, 32L);
  }

  @Test
  void missingExecutionFailsClosedInsteadOfFallingBack() {
    OfflineNotificationPolicyReader reader = mock(OfflineNotificationPolicyReader.class);
    OfflineNotificationPolicyCodec codec =
        new OfflineNotificationPolicyCodec(new ObjectMapper());
    when(reader.find(7L, 99L)).thenReturn(Optional.empty());

    OfflineSyncNotificationPolicyResolver resolver =
        new OfflineSyncNotificationPolicyResolver(reader, codec);

    assertThatThrownBy(() -> resolver.resolve(intent()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("无法解析离线同步通知策略");
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
