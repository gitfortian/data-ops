package io.yak.ops.business.digitalscreen.repository.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/** JSON codec for the frontend-owned component binding document. */
@Component
public class DigitalScreenBindingsCodec {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public DigitalScreenBindingsCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String encode(Map<String, Object> bindings) {
    try {
      return objectMapper.writeValueAsString(bindings == null ? Map.of() : bindings);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("大屏数据绑定配置无法序列化", exception);
    }
  }

  public Map<String, Object> decode(String value) {
    if (value == null || value.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(value, MAP_TYPE);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("数据库中的大屏数据绑定配置损坏", exception);
    }
  }
}
