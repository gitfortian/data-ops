package io.yak.ops.business.sync.offline.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobResponse;
import io.yak.ops.business.sync.offline.engine.plan.OfflineExecutionPlan;
import org.springframework.util.StringUtils;

/** Persisted state of one physical Link-Up child inside a logical FAN_OUT Attempt. */
final class OfflineFanOutUnitState {

  private final String unitKey;
  private final String sourceTable;
  private final String sinkTable;
  private final String externalExecutionId;
  private final String idempotencyKey;
  private final ObjectMapper objectMapper;
  private JsonNode response;

  private OfflineFanOutUnitState(
      String unitKey,
      String sourceTable,
      String sinkTable,
      String externalExecutionId,
      String idempotencyKey,
      JsonNode response,
      ObjectMapper objectMapper) {
    this.unitKey = unitKey;
    this.sourceTable = sourceTable;
    this.sinkTable = sinkTable;
    this.externalExecutionId = externalExecutionId;
    this.idempotencyKey = idempotencyKey;
    this.response = response;
    this.objectMapper = objectMapper;
  }

  static OfflineFanOutUnitState initial(
      OfflineJobExecution execution,
      OfflineExecutionPlan.Unit unit,
      ObjectMapper objectMapper) {
    String external = execution.getExternalExecutionId() + "-f" + unit.unitKey();
    String idempotency = execution.getIdempotencyKey() + ":fanout:" + unit.unitKey();
    ObjectNode response = objectMapper.createObjectNode();
    response.put("externalExecutionId", external);
    response.put("idempotencyKey", idempotency);
    response.put("status", OfflineExecutionStatus.CREATED.name());
    response.put("stateVersion", 0L);
    response.set("metrics", objectMapper.createObjectNode());
    response.set("commitSummary", objectMapper.createObjectNode());
    response.set("pipelines", objectMapper.createArrayNode());
    return new OfflineFanOutUnitState(
        unit.unitKey(),
        unit.sourceTable(),
        unit.sinkTable(),
        external,
        idempotency,
        response,
        objectMapper);
  }

  static OfflineFanOutUnitState fromJson(JsonNode item, ObjectMapper objectMapper) {
    String unitKey = requireText(item, "unitKey");
    String sourceTable = requireText(item, "sourceTable");
    String sinkTable = requireText(item, "sinkTable");
    String external = requireText(item, "externalExecutionId");
    String idempotency = requireText(item, "idempotencyKey");
    JsonNode response = item.path("response");
    if (!response.isObject()) {
      ObjectNode fallback = objectMapper.createObjectNode();
      fallback.put("externalExecutionId", external);
      fallback.put("idempotencyKey", idempotency);
      fallback.put("status", OfflineExecutionStatus.CREATED.name());
      fallback.put("stateVersion", 0L);
      fallback.set("metrics", objectMapper.createObjectNode());
      fallback.set("commitSummary", objectMapper.createObjectNode());
      fallback.set("pipelines", objectMapper.createArrayNode());
      response = fallback;
    } else {
      response = response.deepCopy();
    }
    return new OfflineFanOutUnitState(
        unitKey,
        sourceTable,
        sinkTable,
        external,
        idempotency,
        response,
        objectMapper);
  }

  ObjectNode toJson() {
    ObjectNode item = objectMapper.createObjectNode();
    item.put("unitKey", unitKey);
    item.put("sourceTable", sourceTable);
    item.put("sinkTable", sinkTable);
    item.put("externalExecutionId", externalExecutionId);
    item.put("idempotencyKey", idempotencyKey);
    item.set("response", response.deepCopy());
    return item;
  }

  void apply(LinkUpJobResponse value) {
    if (value != null) {
      response = objectMapper.valueToTree(value);
    }
  }

  LinkUpJobResponse localResponse(
      OfflineExecutionStatus status,
      String errorCode,
      String errorMessage) {
    LinkUpJobResponse value = new LinkUpJobResponse();
    value.setExternalExecutionId(externalExecutionId);
    value.setIdempotencyKey(idempotencyKey);
    value.setJobId(engineJobId());
    value.setWorkerInstanceId(workerInstanceId());
    value.setStatus(status.name());
    value.setStateVersion(stateVersion() + 1L);
    value.setCancellationRequested(status == OfflineExecutionStatus.CANCELED);
    value.setErrorCode(errorCode);
    value.setErrorMessage(errorMessage);
    value.setMetrics(objectMapper.createObjectNode());
    value.setCommitSummary(objectMapper.createObjectNode());
    value.setPipelines(objectMapper.createArrayNode());
    return value;
  }

  String unitKey() {
    return unitKey;
  }

  String sourceTable() {
    return sourceTable;
  }

  String sinkTable() {
    return sinkTable;
  }

  String externalExecutionId() {
    return externalExecutionId;
  }

  String idempotencyKey() {
    return idempotencyKey;
  }

  JsonNode response() {
    return response;
  }

  String engineJobId() {
    return response.path("jobId").asText(null);
  }

  String workerInstanceId() {
    return response.path("workerInstanceId").asText(null);
  }

  String status() {
    return response.path("status").asText(OfflineExecutionStatus.CREATED.name());
  }

  long stateVersion() {
    return response.path("stateVersion").asLong(0L);
  }

  private static String requireText(JsonNode node, String field) {
    String value = node == null ? null : node.path(field).asText(null);
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException("FAN_OUT persisted unit is missing " + field);
    }
    return value.trim();
  }
}
