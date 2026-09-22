package io.yak.ops.business.workflow.repository.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 工作流持久化 JSON 编解码器。 */
@Component
public class WorkflowJsonCodec {
  private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() { };
  private static final TypeReference<Map<String, TaskVersionSnapshot>> TASK_VERSIONS =
      new TypeReference<>() { };

  private final ObjectMapper objectMapper;

  public WorkflowJsonCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化工作流持久化 JSON 失败", exception);
    }
  }

  public <T> T read(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("读取工作流持久化 JSON 失败", exception);
    }
  }

  public Map<String, Object> readMap(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, JSON_MAP);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("读取工作流 JSON Map 失败", exception);
    }
  }

  public Map<String, TaskVersionSnapshot> readTaskVersions(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, TASK_VERSIONS);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("读取工作流任务版本快照失败", exception);
    }
  }

  public boolean sameJson(String left, String right) {
    if (left == null || right == null) return left == right;
    try {
      return objectMapper.readTree(left).equals(objectMapper.readTree(right));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("比较工作流 JSON 失败", exception);
    }
  }
}
