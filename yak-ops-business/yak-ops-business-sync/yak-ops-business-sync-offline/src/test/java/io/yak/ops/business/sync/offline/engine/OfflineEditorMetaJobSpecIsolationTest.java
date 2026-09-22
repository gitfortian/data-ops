package io.yak.ops.business.sync.offline.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OfflineEditorMetaJobSpecIsolationTest {

  @Test
  void editorMetaNeverEntersEngineJobSpecModel() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode definition = objectMapper.readTree("""
        {
          "basic": {"mode": "GUIDE_SINGLE"},
          "source": {"dbType": "MYSQL", "config": {}},
          "sink": {"dbType": "MYSQL", "config": {}},
          "channel": {"parallelism": 1},
          "editorMeta": {
            "icon": {"emoji": "🚀", "background": "#DCEEFF"}
          }
        }
        """);

    JsonNode jobSpecInput =
        OfflineDefinitionModelAdapter.forJobSpec(definition, objectMapper);

    assertThat(jobSpecInput.has("editorMeta")).isFalse();
    assertThat(definition.has("editorMeta")).isTrue();
  }
}
