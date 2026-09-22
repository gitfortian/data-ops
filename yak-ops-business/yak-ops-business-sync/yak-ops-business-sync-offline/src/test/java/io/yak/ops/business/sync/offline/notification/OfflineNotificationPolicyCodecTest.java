package io.yak.ops.business.sync.offline.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobNotificationDTO;
import io.yak.ops.core.notification.NotificationPolicy;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineNotificationPolicyCodecTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OfflineNotificationPolicyCodec codec =
      new OfflineNotificationPolicyCodec(objectMapper);

  @Test
  void legacyNullPolicyKeepsProjectOwnerInAppCompatibility() {
    NotificationPolicy policy = codec.decodePolicy(null);

    assertThat(policy.enabled()).isTrue();
    assertThat(policy.recipientStrategy())
        .isEqualTo(NotificationPolicy.RecipientStrategy.PROJECT_OWNER);
    assertThat(policy.routesTo(NotificationPolicy.Destination.IN_APP)).isTrue();
    assertThat(policy.routesTo(NotificationPolicy.Destination.ALERT)).isFalse();
  }

  @Test
  void explicitUsersAreNormalizedAndMappedToRouterPolicy() {
    OfflineJobNotificationDTO config = new OfflineJobNotificationDTO();
    config.setRecipientType(" explicit_users ");
    config.setRecipientUserIds(Arrays.asList(12L, null, -1L, 12L, 13L));

    String json = codec.encode(config);
    NotificationPolicy policy = codec.decodePolicy(json);

    assertThat(policy.enabled()).isTrue();
    assertThat(policy.recipientStrategy())
        .isEqualTo(NotificationPolicy.RecipientStrategy.EXPLICIT_USERS);
    assertThat(policy.recipientUserIds()).containsExactly(12L, 13L);
    assertThat(policy.routesTo(NotificationPolicy.Destination.IN_APP)).isTrue();
  }

  @Test
  void inAppAndAlertDestinationsCanBeCombined() {
    OfflineJobNotificationDTO config = new OfflineJobNotificationDTO();
    config.setAlertEnabled(true);
    config.setAlertChannelIds(Arrays.asList(7L, null, -1L, 7L, 8L));

    NotificationPolicy policy = codec.decodePolicy(codec.encode(config));

    assertThat(policy.routesTo(NotificationPolicy.Destination.IN_APP)).isTrue();
    assertThat(policy.routesTo(NotificationPolicy.Destination.ALERT)).isTrue();
    assertThat(policy.alertChannelIds()).containsExactly(7L, 8L);
  }

  @Test
  void alertOnlyPolicyDoesNotRequireInAppRecipients() {
    OfflineJobNotificationDTO config = new OfflineJobNotificationDTO();
    config.setInAppEnabled(false);
    config.setRecipientType("EXPLICIT_USERS");
    config.setAlertEnabled(true);
    config.setAlertChannelIds(List.of(7L));

    NotificationPolicy policy = codec.decodePolicy(codec.encode(config));

    assertThat(policy.enabled()).isTrue();
    assertThat(policy.routesTo(NotificationPolicy.Destination.IN_APP)).isFalse();
    assertThat(policy.routesTo(NotificationPolicy.Destination.ALERT)).isTrue();
    assertThat(policy.recipientUserIds()).isEmpty();
  }

  @Test
  void disabledTaskNotificationProducesDisabledPolicy() {
    OfflineJobNotificationDTO config = new OfflineJobNotificationDTO();
    config.setEnabled(false);
    config.setRecipientType("EXPLICIT_USERS");

    NotificationPolicy policy = codec.decodePolicy(codec.encode(config));

    assertThat(policy.enabled()).isFalse();
  }

  @Test
  void dedicatedColumnOverridesStaleEmbeddedEditDetail() throws Exception {
    OfflineJobNotificationDTO config = new OfflineJobNotificationDTO();
    config.setRecipientType("EXPLICIT_USERS");
    config.setRecipientUserIds(List.of(21L));
    config.setAlertEnabled(true);
    config.setAlertChannelIds(List.of(9L));

    JsonNode detail = objectMapper.readTree(
        "{\"id\":10,\"notification\":{\"recipientType\":\"PROJECT_OWNER\"}}");
    JsonNode result = codec.applyToEditDetail(detail, codec.encode(config));

    assertThat(result.path("notification").path("recipientType").asText())
        .isEqualTo("EXPLICIT_USERS");
    assertThat(result.path("notification").path("recipientUserIds").get(0).asLong())
        .isEqualTo(21L);
    assertThat(result.path("notification").path("alertEnabled").asBoolean()).isTrue();
    assertThat(result.path("notification").path("alertChannelIds").get(0).asLong())
        .isEqualTo(9L);
  }

  @Test
  void legacyNullColumnRemovesPotentialEmbeddedNotification() throws Exception {
    JsonNode detail = objectMapper.readTree(
        "{\"id\":10,\"notification\":{\"enabled\":false}}");

    JsonNode result = codec.applyToEditDetail(detail, null);

    assertThat(result.has("notification")).isFalse();
  }

  @Test
  void activeExplicitUserPolicyRequiresAtLeastOneRecipient() {
    OfflineJobNotificationDTO config = new OfflineJobNotificationDTO();
    config.setRecipientType("EXPLICIT_USERS");

    assertThatThrownBy(() -> codec.encode(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("至少选择一个用户");
  }

  @Test
  void activeAlertPolicyRequiresAtLeastOneChannel() {
    OfflineJobNotificationDTO config = new OfflineJobNotificationDTO();
    config.setAlertEnabled(true);

    assertThatThrownBy(() -> codec.encode(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("至少需要选择一个告警渠道");
  }
}
