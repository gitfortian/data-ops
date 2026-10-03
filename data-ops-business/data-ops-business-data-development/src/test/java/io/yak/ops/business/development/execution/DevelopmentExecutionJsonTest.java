package io.yak.ops.business.development.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionSummary;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionDetail;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionSubmission;
import org.junit.jupiter.api.Test;

class DevelopmentExecutionJsonTest {
  @Test
  void shouldPreserveIdentifiersBeyondJavascriptSafeIntegerInEveryExecutionResponse() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String id = "2106323980852518913";
    for (Class<?> type : new Class<?>[] { DevelopmentTaskExecutionSummary.class,
        DevelopmentTaskExecutionDetail.class, DevelopmentTaskExecutionSubmission.class }) {
      String jsonInput = "{\"id\":" + id + ",\"nodeId\":" + id
          + ",\"taskType\":\"SQL\",\"status\":\"RUNNING\"}";
      Object response = mapper.readValue(jsonInput, type);
      var json = mapper.readTree(mapper.writeValueAsString(response));
      assertThat(json.get("nodeId").isTextual()).as(type.getSimpleName()).isTrue();
      assertThat(json.get("nodeId").asText()).isEqualTo(id);
      assertThat(json.get("id").isTextual()).as(type.getSimpleName()).isTrue();
      assertThat(json.get("id").asText()).isEqualTo(id);
    }
  }

  @Test
  void retryIdentifiersAreStringsWhileDurationRemainsNumeric() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String id = "2106323980852518913";
    for (Class<?> type : new Class<?>[] { DevelopmentTaskExecutionSummary.class, DevelopmentTaskExecutionDetail.class }) {
      Object response = mapper.readValue("{\"retryOfExecutionId\":" + id + ",\"durationMs\":42}", type);
      var json = mapper.readTree(mapper.writeValueAsString(response));
      assertThat(json.get("retryOfExecutionId").isTextual()).isTrue();
      assertThat(json.get("retryOfExecutionId").asText()).isEqualTo(id);
      assertThat(json.get("durationMs").isNumber()).isTrue();
    }
  }
}
