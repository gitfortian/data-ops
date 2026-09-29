package io.yak.ops.plugin.alert.dingtalk;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Internal JSON mapper for DingTalk config and request serialization. */
final class DingTalkConfigMapper {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private DingTalkConfigMapper() {}

  static DingTalkAlertConfig parse(String configJson) throws Exception {
    return MAPPER.readValue(configJson, DingTalkAlertConfig.class);
  }

  static String serialize(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("JSON serialization failed: " + e.getMessage(), e);
    }
  }
}
