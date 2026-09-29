package io.yak.ops.business.sync.offline.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobNotificationDTO;
import io.yak.ops.core.notification.NotificationPolicy;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Normalizes Offline Sync task notification configuration and maps it to Router policy. */
@Component
@ConditionalOnOfflineSyncEnabled
public class OfflineNotificationPolicyCodec {

  public static final String FINAL_FAILURE = "FINAL_FAILURE";
  public static final String PROJECT_OWNER = "PROJECT_OWNER";
  public static final String EXPLICIT_USERS = "EXPLICIT_USERS";

  private final ObjectMapper objectMapper;

  public OfflineNotificationPolicyCodec(
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Null means legacy task and intentionally keeps the default compatibility policy. */
  public String encode(OfflineJobNotificationDTO value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(normalize(value));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化离线同步通知策略失败", exception);
    }
  }

  /**
   * The dedicated notification column is the source of truth for edit responses.
   *
   * <p>Old definition_json payloads may not contain notification at all, while older clients may
   * keep stale embedded notification data. Always project the normalized dedicated column into the
   * response and remove the embedded field for legacy NULL policies.</p>
   */
  public JsonNode applyToEditDetail(JsonNode detail, String json) {
    if (detail == null || !detail.isObject()) {
      throw new IllegalArgumentException("离线同步编辑详情必须是 JSON 对象");
    }
    ObjectNode result = ((ObjectNode) detail).deepCopy();
    if (!StringUtils.hasText(json)) {
      result.remove("notification");
      return result;
    }
    try {
      OfflineJobNotificationDTO normalized =
          normalize(objectMapper.readValue(json, OfflineJobNotificationDTO.class));
      result.set("notification", objectMapper.valueToTree(normalized));
      return result;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("离线同步通知策略 JSON 已损坏", exception);
    }
  }

  public NotificationPolicy decodePolicy(String json) {
    if (!StringUtils.hasText(json)) {
      return NotificationPolicy.projectOwnersInApp();
    }
    try {
      OfflineJobNotificationDTO config =
          normalize(objectMapper.readValue(json, OfflineJobNotificationDTO.class));
      if (!Boolean.TRUE.equals(config.getEnabled())
          || !config.getTriggers().contains(FINAL_FAILURE)) {
        return NotificationPolicy.disabled();
      }

      Set<NotificationPolicy.Destination> destinations =
          EnumSet.noneOf(NotificationPolicy.Destination.class);
      if (Boolean.TRUE.equals(config.getInAppEnabled())) {
        destinations.add(NotificationPolicy.Destination.IN_APP);
      }
      if (Boolean.TRUE.equals(config.getAlertEnabled())) {
        destinations.add(NotificationPolicy.Destination.ALERT);
      }
      if (destinations.isEmpty()) {
        return NotificationPolicy.disabled();
      }

      NotificationPolicy.RecipientStrategy recipientStrategy =
          EXPLICIT_USERS.equals(config.getRecipientType())
              ? NotificationPolicy.RecipientStrategy.EXPLICIT_USERS
              : NotificationPolicy.RecipientStrategy.PROJECT_OWNER;
      List<Long> recipientUserIds =
          recipientStrategy == NotificationPolicy.RecipientStrategy.EXPLICIT_USERS
              ? config.getRecipientUserIds()
              : List.of();
      List<Long> alertChannelIds = Boolean.TRUE.equals(config.getAlertEnabled())
          ? config.getAlertChannelIds()
          : List.of();

      return new NotificationPolicy(
          true,
          recipientStrategy,
          recipientUserIds,
          destinations,
          alertChannelIds);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("离线同步通知策略 JSON 已损坏", exception);
    }
  }

  public OfflineJobNotificationDTO normalize(OfflineJobNotificationDTO value) {
    if (value == null) {
      throw new IllegalArgumentException("通知策略不能为空");
    }
    OfflineJobNotificationDTO normalized = new OfflineJobNotificationDTO();
    normalized.setEnabled(value.getEnabled() == null ? Boolean.TRUE : value.getEnabled());
    normalized.setInAppEnabled(
        value.getInAppEnabled() == null ? Boolean.TRUE : value.getInAppEnabled());
    normalized.setAlertEnabled(Boolean.TRUE.equals(value.getAlertEnabled()));

    List<String> triggers = value.getTriggers() == null || value.getTriggers().isEmpty()
        ? List.of(FINAL_FAILURE)
        : value.getTriggers().stream()
            .filter(Objects::nonNull)
            .map(item -> item.trim().toUpperCase(Locale.ROOT))
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    if (triggers.isEmpty() || triggers.stream().anyMatch(item -> !FINAL_FAILURE.equals(item))) {
      throw new IllegalArgumentException("离线同步通知触发条件仅支持 FINAL_FAILURE");
    }
    normalized.setTriggers(triggers);

    String recipientType = StringUtils.hasText(value.getRecipientType())
        ? value.getRecipientType().trim().toUpperCase(Locale.ROOT)
        : PROJECT_OWNER;
    if (!PROJECT_OWNER.equals(recipientType) && !EXPLICIT_USERS.equals(recipientType)) {
      throw new IllegalArgumentException("通知接收人类型仅支持 PROJECT_OWNER 或 EXPLICIT_USERS");
    }
    normalized.setRecipientType(recipientType);

    List<Long> userIds = normalizeIds(value.getRecipientUserIds());
    if (EXPLICIT_USERS.equals(recipientType)
        && Boolean.TRUE.equals(normalized.getEnabled())
        && Boolean.TRUE.equals(normalized.getInAppEnabled())
        && userIds.isEmpty()) {
      throw new IllegalArgumentException("指定用户通知至少需要选择一个用户");
    }
    normalized.setRecipientUserIds(EXPLICIT_USERS.equals(recipientType) ? userIds : List.of());

    List<Long> alertChannelIds = normalizeIds(value.getAlertChannelIds());
    if (Boolean.TRUE.equals(normalized.getEnabled())
        && Boolean.TRUE.equals(normalized.getAlertEnabled())
        && alertChannelIds.isEmpty()) {
      throw new IllegalArgumentException("外部告警通知至少需要选择一个告警渠道");
    }
    normalized.setAlertChannelIds(
        Boolean.TRUE.equals(normalized.getAlertEnabled()) ? alertChannelIds : List.of());
    return normalized;
  }

  private List<Long> normalizeIds(List<Long> ids) {
    return ids == null
        ? List.of()
        : ids.stream()
            .filter(Objects::nonNull)
            .filter(id -> id > 0L)
            .distinct()
            .toList();
  }
}
