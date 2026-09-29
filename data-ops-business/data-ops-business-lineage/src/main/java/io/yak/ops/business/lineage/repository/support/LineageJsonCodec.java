package io.yak.ops.business.lineage.repository.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** JSON codec kept at the persistence adapter boundary. */
@Component
@RequiredArgsConstructor
public class LineageJsonCodec {

  private final ObjectMapper objectMapper;

  public String write(JsonNode value) {
    if (value == null || value.isNull()) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("血缘 properties 不是有效 JSON", exception);
    }
  }

  public JsonNode read(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("数据库中的血缘 properties 不是有效 JSON", exception);
    }
  }
}
