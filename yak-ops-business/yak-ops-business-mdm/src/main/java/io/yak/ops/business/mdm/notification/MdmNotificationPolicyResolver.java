package io.yak.ops.business.mdm.notification;

import io.yak.ops.business.mdm.domain.subscription.MdmSubscription;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSubscriptionRepository;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationPolicy;
import io.yak.ops.core.notification.NotificationPolicyResolver;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 主数据通知策略(R6):一条 MDM 事件只发给「该实体的生效订阅方」。
 *
 * <p>刻意不回落项目所有者——订阅表就是收件人真相,没人订阅即不发,否则总览/审批期
 * 会给不相干的人刷一堆站内信。订阅方编码解析不到平台用户时同样视为不投递,
 * 该事实由订阅列表的「站内信可达」列如实呈现(一期无 WEBHOOK 出口)。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MdmNotificationPolicyResolver implements NotificationPolicyResolver {

  private static final int ORDER = 100;

  private final MdmSubscriptionRepository subscriptionRepository;
  private final MdmUserDirectory userDirectory;

  @Override
  public boolean supports(NotificationIntent intent) {
    return intent != null && MdmNotifier.SOURCE_TYPES.contains(intent.sourceType());
  }

  @Override
  public NotificationPolicy resolve(NotificationIntent intent) {
    Long entityId = parseEntityId(intent);
    if (entityId == null) {
      return NotificationPolicy.disabled();
    }
    List<MdmSubscription> subscriptions;
    try {
      subscriptions = subscriptionRepository.listActiveByEntity(entityId);
    } catch (RuntimeException failure) {
      log.warn("主数据订阅反查失败,本次不投递: entity={}", entityId, failure);
      return NotificationPolicy.disabled();
    }
    Set<Long> recipients = new LinkedHashSet<>();
    for (MdmSubscription subscription :
        subscriptions == null ? List.<MdmSubscription>of() : subscriptions) {
      if (!MdmSubscription.NOTIFY_MODE_EVENT.equalsIgnoreCase(subscription.notifyMode())) {
        continue;
      }
      userDirectory
          .userIdOf(subscription.subscriberCode())
          .filter(userId -> userId > 0L)
          .ifPresent(recipients::add);
    }
    if (recipients.isEmpty()) {
      return NotificationPolicy.disabled();
    }
    return new NotificationPolicy(
        true,
        NotificationPolicy.RecipientStrategy.EXPLICIT_USERS,
        List.copyOf(recipients),
        Set.of(NotificationPolicy.Destination.IN_APP),
        List.of());
  }

  @Override
  public int order() {
    return ORDER;
  }

  private static Long parseEntityId(NotificationIntent intent) {
    try {
      long parsed = Long.parseLong(intent.sourceId().trim());
      return parsed > 0L ? parsed : null;
    } catch (RuntimeException failure) {
      return null;
    }
  }
}
