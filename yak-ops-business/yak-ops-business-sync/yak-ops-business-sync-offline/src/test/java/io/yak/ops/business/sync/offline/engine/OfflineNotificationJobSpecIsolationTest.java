package io.yak.ops.business.sync.offline.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OfflineNotificationJobSpecIsolationTest {

  @Test
  void notificationPolicyNeverEntersEngineJobSpecModel() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode definition = objectMapper.readTree("""
        {
          "basic": {"mode": "GUIDE_SINGLE"},
          "source": {"dbType": "MYSQL", "config": {}},
          "sink": {"dbType": "MYSQL", "config": {}},
          "channel": {"parallelism": 1},
          "notification": {
            "enabled": true,
            "recipientType": "EXPLICIT_USERS",
            "recipientUserIds": [11]
          }
        }
        """);

    JsonNode jobSpecInput =
        OfflineDefinitionModelAdapter.forJobSpec(definition, objectMapper);

    assertThat(jobSpecInput.has("notification")).isFalse();
    assertThat(definition.has("notification")).isTrue();
  }
}
