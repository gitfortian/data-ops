package io.yak.ops.business.dashboard.repository.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Persistence codec for Dashboard JSON columns. */
@Component
public class DashboardJsonCodec {

  private final ObjectMapper objectMapper;

  public DashboardJsonCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Dashboard JSON 序列化失败", exception);
    }
  }

  public Object read(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return objectMapper.readValue(value, Object.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Dashboard JSON 反序列化失败", exception);
    }
  }
}
