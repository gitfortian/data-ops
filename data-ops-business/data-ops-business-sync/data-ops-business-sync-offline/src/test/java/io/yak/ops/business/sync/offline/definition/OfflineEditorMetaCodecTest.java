package io.yak.ops.business.sync.offline.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobEditorMetaDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobIconDTO;
import org.junit.jupiter.api.Test;

class OfflineEditorMetaCodecTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OfflineEditorMetaCodec codec = new OfflineEditorMetaCodec(objectMapper);

  @Test
  void dedicatedEditorMetaOverridesStaleEmbeddedValue() throws Exception {
    OfflineJobIconDTO icon = new OfflineJobIconDTO();
    icon.setEmoji("🚀");
    icon.setBackground("#dceeff");
    OfflineJobEditorMetaDTO meta = new OfflineJobEditorMetaDTO();
    meta.setIcon(icon);

    JsonNode detail = objectMapper.readTree(
        "{\"id\":10,\"editorMeta\":{\"icon\":{\"emoji\":\"🤖\",\"background\":\"#FFFFFF\"}}}");
    JsonNode result = codec.applyToEditDetail(detail, codec.encode(meta));

    assertThat(result.path("editorMeta").path("icon").path("emoji").asText())
        .isEqualTo("🚀");
    assertThat(result.path("editorMeta").path("icon").path("background").asText())
        .isEqualTo("#DCEEFF");
  }

  @Test
  void legacyNullColumnRemovesPotentialEmbeddedEditorMeta() throws Exception {
    JsonNode detail = objectMapper.readTree(
        "{\"id\":10,\"editorMeta\":{\"icon\":{\"emoji\":\"🤖\",\"background\":\"#FFE7D6\"}}}");

    JsonNode result = codec.applyToEditDetail(detail, null);

    assertThat(result.has("editorMeta")).isFalse();
  }

  @Test
  void invalidIconBackgroundIsRejected() {
    OfflineJobIconDTO icon = new OfflineJobIconDTO();
    icon.setEmoji("🤖");
    icon.setBackground("red");
    OfflineJobEditorMetaDTO meta = new OfflineJobEditorMetaDTO();
    meta.setIcon(icon);

    assertThatThrownBy(() -> codec.encode(meta))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("#RRGGBB");
  }
}
