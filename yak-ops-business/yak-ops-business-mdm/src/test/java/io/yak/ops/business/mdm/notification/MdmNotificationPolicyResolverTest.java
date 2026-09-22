package io.yak.ops.business.mdm.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.mdm.domain.subscription.MdmSubscription;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSubscriptionRepository;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationPolicy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 主数据通知策略测试(R6):收件人只来自该实体的生效 EVENT 订阅,且解析不到用户时宁可不发
 * (回落项目所有者会让订阅表形同虚设,并给不相干的人刷屏)。
 */
class MdmNotificationPolicyResolverTest {

  private MdmSubscriptionRepository subscriptionRepository;
  private MdmUserDirectory userDirectory;
  private MdmNotificationPolicyResolver resolver;

  @BeforeEach
  void setUp() {
    subscriptionRepository = mock(MdmSubscriptionRepository.class);
    userDirectory = mock(MdmUserDirectory.class);
    resolver = new MdmNotificationPolicyResolver(subscriptionRepository, userDirectory);
  }

  private static NotificationIntent intent(String sourceType, String sourceId) {
    return new NotificationIntent(
        1L, NotificationIntent.Type.SYSTEM, NotificationIntent.Level.SUCCESS,
        "主数据变更已生效", null, null, sourceType, sourceId, "/mdm/modeling/7");
  }

  private static MdmSubscription subscription(String code, String notifyMode) {
    return new MdmSubscription(
        1L, 7L, code, code + " 名称", notifyMode, MdmSubscription.STATUS_ACTIVE,
        "root", LocalDateTime.now(), LocalDateTime.now());
  }

  @Test
  void supportsOnlyMdmEventSourceTypes() {
    assertTrue(resolver.supports(intent(MdmNotifier.SOURCE_CHANGE_APPLIED, "7")));
    assertTrue(resolver.supports(intent(MdmNotifier.SOURCE_MERGE_COMPLETED, "7")));
    assertTrue(resolver.supports(intent(MdmNotifier.SOURCE_DISTRIBUTION_PUBLISHED, "7")));
    assertFalse(resolver.supports(intent("DATA_QUALITY_EXECUTION", "7")));
    assertFalse(resolver.supports(null));
  }

  @Test
  void routesToResolvedEventSubscribersOnly() {
    when(subscriptionRepository.listActiveByEntity(7L))
        .thenReturn(List.of(subscription("alice", "EVENT"), subscription("bob", "EVENT")));
    when(userDirectory.userIdOf("alice")).thenReturn(Optional.of(11L));
    when(userDirectory.userIdOf("bob")).thenReturn(Optional.of(12L));

    NotificationPolicy policy = resolver.resolve(intent(MdmNotifier.SOURCE_CHANGE_APPLIED, "7"));

    assertTrue(policy.enabled());
    assertEquals(NotificationPolicy.RecipientStrategy.EXPLICIT_USERS, policy.recipientStrategy());
    assertEquals(List.of(11L, 12L), policy.recipientUserIds());
    assertTrue(policy.routesTo(NotificationPolicy.Destination.IN_APP));
    assertFalse(policy.routesTo(NotificationPolicy.Destination.ALERT));
  }

  /** 编码解析不到平台用户 = 发不到人,直接剔除而不是报错(列表已如实标注可达性)。 */
  @Test
  void dropsSubscribersThatAreNotPlatformUsers() {
    when(subscriptionRepository.listActiveByEntity(7L))
        .thenReturn(List.of(subscription("ghost", "EVENT"), subscription("alice", "EVENT")));
    when(userDirectory.userIdOf("ghost")).thenReturn(Optional.empty());
    when(userDirectory.userIdOf("alice")).thenReturn(Optional.of(11L));

    NotificationPolicy policy = resolver.resolve(intent(MdmNotifier.SOURCE_MERGE_COMPLETED, "7"));

    assertEquals(List.of(11L), policy.recipientUserIds());
  }

  @Test
  void ignoresNonEventModes() {
    when(subscriptionRepository.listActiveByEntity(7L))
        .thenReturn(List.of(subscription("alice", MdmSubscription.NOTIFY_MODE_WEBHOOK)));

    NotificationPolicy policy =
        resolver.resolve(intent(MdmNotifier.SOURCE_DISTRIBUTION_PUBLISHED, "7"));

    assertFalse(policy.enabled());
    verify(userDirectory, never()).userIdOf(any());
  }

  /** 无人订阅即不发:绝不回落项目所有者。 */
  @Test
  void staysSilentWithoutSubscribersInsteadOfFallingBackToProjectOwner() {
    when(subscriptionRepository.listActiveByEntity(7L)).thenReturn(List.of());

    NotificationPolicy policy = resolver.resolve(intent(MdmNotifier.SOURCE_CHANGE_APPLIED, "7"));

    assertFalse(policy.enabled());
    assertEquals(NotificationPolicy.RecipientStrategy.PROJECT_OWNER, policy.recipientStrategy());
    assertTrue(policy.recipientUserIds().isEmpty());
  }

  @Test
  void disablesWhenSubscribersResolveToNobody() {
    when(subscriptionRepository.listActiveByEntity(7L))
        .thenReturn(List.of(subscription("ghost", "EVENT")));
    when(userDirectory.userIdOf("ghost")).thenReturn(Optional.empty());

    assertFalse(resolver.resolve(intent(MdmNotifier.SOURCE_CHANGE_APPLIED, "7")).enabled());
  }

  /** sourceId 不是实体 id 属于上游接线错误:安静跳过,不能因为反查异常影响业务。 */
  @Test
  void rejectsNonNumericSourceIdWithoutTouchingRepository() {
    assertFalse(resolver.resolve(intent(MdmNotifier.SOURCE_CHANGE_APPLIED, "abc")).enabled());
    assertFalse(resolver.resolve(intent(MdmNotifier.SOURCE_CHANGE_APPLIED, "0")).enabled());
    verify(subscriptionRepository, never()).listActiveByEntity(anyLong());
  }

  @Test
  void subscriptionLookupFailureDegradesToNoDelivery() {
    when(subscriptionRepository.listActiveByEntity(7L))
        .thenThrow(new IllegalStateException("db down"));

    assertFalse(resolver.resolve(intent(MdmNotifier.SOURCE_CHANGE_APPLIED, "7")).enabled());
  }

  @Test
  void runsBeforeTheProjectOwnerFallback() {
    assertTrue(resolver.order() < Integer.MAX_VALUE);
  }
}
