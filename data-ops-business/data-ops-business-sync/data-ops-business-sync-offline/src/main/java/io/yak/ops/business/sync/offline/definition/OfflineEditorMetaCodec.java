package io.yak.ops.business.sync.offline.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobEditorMetaDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobIconDTO;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Encodes UI-only metadata without coupling it to the executable Offline Sync JobSpec. */
@Component
@ConditionalOnOfflineSyncEnabled
public class OfflineEditorMetaCodec {

  private static final int MAX_EMOJI_LENGTH = 32;
  private static final String HEX_COLOR_PATTERN = "#[0-9a-fA-F]{6}";

  private final ObjectMapper objectMapper;

  public OfflineEditorMetaCodec(
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String encode(OfflineJobEditorMetaDTO value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(normalize(value));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化离线同步编辑器元数据失败", exception);
    }
  }

  public JsonNode applyToEditDetail(JsonNode detail, String json) {
    if (detail == null || !detail.isObject()) {
      throw new IllegalArgumentException("离线同步编辑详情必须是 JSON 对象");
    }
    ObjectNode result = ((ObjectNode) detail).deepCopy();
    if (!StringUtils.hasText(json)) {
      result.remove("editorMeta");
      return result;
    }
    try {
      OfflineJobEditorMetaDTO normalized =
          normalize(objectMapper.readValue(json, OfflineJobEditorMetaDTO.class));
      result.set("editorMeta", objectMapper.valueToTree(normalized));
      return result;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("离线同步编辑器元数据 JSON 已损坏", exception);
    }
  }

  OfflineJobEditorMetaDTO normalize(OfflineJobEditorMetaDTO value) {
    if (value == null) {
      throw new IllegalArgumentException("编辑器元数据不能为空");
    }
    OfflineJobEditorMetaDTO normalized = new OfflineJobEditorMetaDTO();
    if (value.getIcon() == null) {
      return normalized;
    }

    String emoji = trim(value.getIcon().getEmoji());
    String background = trim(value.getIcon().getBackground());
    if (!StringUtils.hasText(emoji) || !StringUtils.hasText(background)) {
      throw new IllegalArgumentException("任务图标必须同时包含 emoji 和 background");
    }
    if (emoji.length() > MAX_EMOJI_LENGTH) {
      throw new IllegalArgumentException("任务图标 emoji 长度不能超过 32 个字符");
    }
    if (!background.matches(HEX_COLOR_PATTERN)) {
      throw new IllegalArgumentException("任务图标 background 必须是 #RRGGBB 颜色");
    }

    OfflineJobIconDTO icon = new OfflineJobIconDTO();
    icon.setEmoji(emoji);
    icon.setBackground(background.toUpperCase(Locale.ROOT));
    normalized.setIcon(icon);
    return normalized;
  }

  private String trim(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
