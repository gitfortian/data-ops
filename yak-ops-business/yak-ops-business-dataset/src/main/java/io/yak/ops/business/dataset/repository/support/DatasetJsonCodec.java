package io.yak.ops.business.dataset.repository.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataset.DatasetFieldDefinition;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Persistence JSON codec. PO keeps JSON as String and Domain stays Jackson-free. */
@Component
@RequiredArgsConstructor
public class DatasetJsonCodec {

  private final ObjectMapper objectMapper;

  public String schemaSnapshot(List<DatasetFieldDefinition> fields) {
    try {
      return objectMapper.writeValueAsString(fields == null ? List.of() : fields);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Dataset schemaSnapshot 序列化失败", exception);
    }
  }
}
