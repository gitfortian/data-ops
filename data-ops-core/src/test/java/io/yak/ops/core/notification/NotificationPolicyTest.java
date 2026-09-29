package io.yak.ops.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NotificationPolicyTest {

  @Test
  void defaultPolicyPreservesProjectOwnerInAppBehavior() {
    NotificationPolicy policy = NotificationPolicy.projectOwnersInApp();

    assertThat(policy.enabled()).isTrue();
    assertThat(policy.recipientStrategy())
        .isEqualTo(NotificationPolicy.RecipientStrategy.PROJECT_OWNER);
    assertThat(policy.recipientUserIds()).isEmpty();
    assertThat(policy.routesTo(NotificationPolicy.Destination.IN_APP)).isTrue();
    assertThat(policy.routesTo(NotificationPolicy.Destination.ALERT)).isFalse();
    assertThat(policy.alertChannelIds()).isEmpty();
  }

  @Test
  void normalizesExplicitRecipientsAndAlertChannelIds() {
    NotificationPolicy policy = new NotificationPolicy(
        true,
        NotificationPolicy.RecipientStrategy.EXPLICIT_USERS,
        java.util.Arrays.asList(11L, null, -1L, 11L, 12L),
        Set.of(NotificationPolicy.Destination.IN_APP, NotificationPolicy.Destination.ALERT),
        java.util.Arrays.asList(3L, null, -1L, 3L, 5L));

    assertThat(policy.recipientUserIds()).containsExactly(11L, 12L);
    assertThat(policy.alertChannelIds()).containsExactly(3L, 5L);
  }

  @Test
  void enabledPolicyRequiresDestination() {
    assertThatThrownBy(() -> new NotificationPolicy(
        true,
        NotificationPolicy.RecipientStrategy.PROJECT_OWNER,
        List.of(),
        Set.of(),
        List.of()))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(NotificationPolicy.disabled().enabled()).isFalse();
  }

  @Test
  void explicitInAppPolicyRequiresRecipients() {
    assertThatThrownBy(() -> new NotificationPolicy(
        true,
        NotificationPolicy.RecipientStrategy.EXPLICIT_USERS,
        List.of(),
        Set.of(NotificationPolicy.Destination.IN_APP),
        List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void alertPolicyRequiresConfiguredChannelIds() {
    assertThatThrownBy(() -> new NotificationPolicy(
        true,
        NotificationPolicy.RecipientStrategy.PROJECT_OWNER,
        List.of(),
        Set.of(NotificationPolicy.Destination.ALERT),
        List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("alert channel ids");
  }
}
