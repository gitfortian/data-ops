package io.yak.ops.business.analysis.repository.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import io.yak.ops.business.analysis.visualization.AnalysisVisualConfig;
import org.springframework.stereotype.Component;

/** Persistence codec for the JSON columns owned by yak_analysis. */
@Component
public class AnalysisJsonCodec {

  private final ObjectMapper objectMapper;

  public AnalysisJsonCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String writeQuerySpec(AnalysisQuerySpec value) {
    return write(value, "Analysis querySpec 序列化失败");
  }

  public String writeVisualConfig(AnalysisVisualConfig value) {
    return write(value, "Analysis visualConfig 序列化失败");
  }

  public AnalysisQuerySpec readQuerySpec(String value, long analysisId) {
    return read(value, AnalysisQuerySpec.class, "Analysis querySpec 反序列化失败：" + analysisId);
  }

  public AnalysisVisualConfig readVisualConfig(String value, long analysisId) {
    return read(
        value,
        AnalysisVisualConfig.class,
        "Analysis visualConfig 反序列化失败：" + analysisId);
  }

  private String write(Object value, String message) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(message, exception);
    }
  }

  private <T> T read(String value, Class<T> type, String message) {
    try {
      return objectMapper.readValue(value, type);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(message, exception);
    }
  }
}
