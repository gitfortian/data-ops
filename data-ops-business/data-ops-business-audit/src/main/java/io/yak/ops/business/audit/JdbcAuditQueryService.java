package io.yak.ops.business.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** JDBC read model for the global Audit Center. */
final class JdbcAuditQueryService implements AuditQueryService {

  private static final Logger log = LoggerFactory.getLogger(JdbcAuditQueryService.class);
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private static final String OPERATION_COLUMNS =
      """
      operation_id, operation_type, operation_name, actor_id, actor_name,
      project_id, project_name, resource_type, resource_id, resource_name,
      status, source, started_at, finished_at, root_trace_id, error_code, summary, metadata_json
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final AuditTimelineRendererRegistry rendererRegistry;

  JdbcAuditQueryService(
      DataSource dataSource,
      ObjectMapper objectMapper,
      AuditTimelineRendererRegistry rendererRegistry) {
    this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    this.objectMapper = objectMapper;
    this.rendererRegistry = rendererRegistry;
  }

  @Override
  public AuditPage<AuditOperationSummary> page(AuditOperationQuery suppliedQuery) {
    AuditOperationQuery query =
        suppliedQuery == null ? AuditOperationQuery.empty() : suppliedQuery;
    QuerySpec spec = operationWhere(query);
    Long totalValue =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM yak_audit_operation" + spec.where(),
            spec.params(),
            Long.class);
    long total = totalValue == null ? 0L : totalValue;

    spec.params().addValue("limit", query.size());
    spec.params().addValue("offset", (long) (query.page() - 1) * query.size());
    List<AuditOperationSummary> records =
        jdbcTemplate.query(
            "SELECT "
                + OPERATION_COLUMNS
                + " FROM yak_audit_operation"
                + spec.where()
                + " ORDER BY started_at DESC, id DESC LIMIT :limit OFFSET :offset",
            spec.params(),
            (resultSet, rowNum) -> operationSummary(resultSet));
    return new AuditPage<>(records, total, query.page(), query.size());
  }

  @Override
  public Optional<AuditOperationDetail> detail(String operationId) {
    String normalizedId = normalize(operationId);
    if (normalizedId == null) return Optional.empty();

    MapSqlParameterSource params = new MapSqlParameterSource("operationId", normalizedId);
    List<OperationRow> operations =
        jdbcTemplate.query(
            "SELECT "
                + OPERATION_COLUMNS
                + " FROM yak_audit_operation WHERE operation_id = :operationId LIMIT 1",
            params,
            (resultSet, rowNum) ->
                new OperationRow(
                    operationSummary(resultSet),
                    jsonMap(resultSet.getString("metadata_json"))));
    if (operations.isEmpty()) return Optional.empty();

    List<AuditTimelineEvent> events =
        jdbcTemplate.query(
            """
            SELECT id, event_type, event_category, event_status, occurred_at, actor_id,
                   resource_type, resource_id, trace_id, span_id, parent_event_id,
                   reason_code, message, payload_json
              FROM yak_audit_event
             WHERE operation_id = :operationId
             ORDER BY occurred_at ASC, id ASC
            """,
            params,
            (resultSet, rowNum) -> timelineEvent(resultSet));
    OperationRow operation = operations.get(0);
    return Optional.of(
        new AuditOperationDetail(operation.summary(), operation.metadata(), events));
  }

  @Override
  public AuditFilterOptions options() {
    return new AuditFilterOptions(
        optionQuery(
            """
            SELECT actor_id AS option_value,
                   COALESCE(MAX(NULLIF(actor_name, '')), actor_id) AS option_label
              FROM yak_audit_operation
             WHERE actor_id IS NOT NULL AND actor_id <> ''
             GROUP BY actor_id
             ORDER BY option_label ASC
             LIMIT 200
            """),
        optionQuery(
            """
            SELECT CAST(project_id AS CHAR) AS option_value,
                   COALESCE(MAX(NULLIF(project_name, '')), CAST(project_id AS CHAR)) AS option_label
              FROM yak_audit_operation
             WHERE project_id IS NOT NULL
             GROUP BY project_id
             ORDER BY option_label ASC
             LIMIT 200
            """),
        simpleDistinctOptions("operation_type"),
        simpleDistinctOptions("resource_type"),
        simpleDistinctOptions("status"),
        simpleDistinctOptions("source"));
  }

  @Override
  public Map<String, String> firstActorNames(
      String operationType,
      String resourceType,
      List<String> resourceIds,
      Long projectId) {
    String normalizedOperationType = normalize(operationType);
    String normalizedResourceType = normalize(resourceType);
    if (normalizedOperationType == null
        || normalizedResourceType == null
        || resourceIds == null
        || resourceIds.isEmpty()) {
      return Map.of();
    }

    List<String> normalizedIds = resourceIds.stream()
        .map(this::normalize)
        .filter(value -> value != null)
        .distinct()
        .toList();
    if (normalizedIds.isEmpty()) return Map.of();

    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("operationType", normalizedOperationType)
        .addValue("resourceType", normalizedResourceType)
        .addValue("resourceIds", normalizedIds);
    String projectPredicate = "";
    if (projectId != null && projectId > 0L) {
      projectPredicate = " AND project_id = :projectId";
      params.addValue("projectId", projectId);
    }

    String sql =
        "SELECT o.resource_id, o.actor_name "
            + "FROM yak_audit_operation o "
            + "JOIN ("
            + "  SELECT resource_id, MIN(id) AS first_id "
            + "  FROM yak_audit_operation "
            + "  WHERE operation_type = :operationType "
            + "    AND resource_type = :resourceType "
            + "    AND resource_id IN (:resourceIds)"
            + projectPredicate
            + "  GROUP BY resource_id"
            + ") first_operation ON first_operation.first_id = o.id";

    Map<String, String> result = new LinkedHashMap<>();
    jdbcTemplate.query(
        sql,
        params,
        resultSet -> {
          String resourceId = normalize(resultSet.getString("resource_id"));
          String actorName = normalize(resultSet.getString("actor_name"));
          if (resourceId != null && actorName != null) result.put(resourceId, actorName);
        });
    return Map.copyOf(result);
  }

  private QuerySpec operationWhere(AuditOperationQuery query) {
    StringBuilder where = new StringBuilder(" WHERE 1 = 1");
    MapSqlParameterSource params = new MapSqlParameterSource();
    if (query.keyword() != null) {
      where.append(
          " AND (operation_id LIKE :keyword OR operation_name LIKE :keyword"
              + " OR resource_name LIKE :keyword OR resource_id LIKE :keyword"
              + " OR summary LIKE :keyword)");
      params.addValue("keyword", "%" + query.keyword() + "%");
    }
    if (query.actor() != null) {
      where.append(" AND (actor_id = :actor OR actor_name = :actor)");
      params.addValue("actor", query.actor());
    }
    if (query.projectId() != null) {
      where.append(" AND project_id = :projectId");
      params.addValue("projectId", query.projectId());
    }
    addEquals(where, params, "operation_type", "operationType", query.operationType());
    addEquals(where, params, "resource_type", "resourceType", query.resourceType());
    addEquals(where, params, "status", "status", query.status());
    addEquals(where, params, "source", "source", query.source());
    if (query.startTime() != null) {
      where.append(" AND started_at >= :startTime");
      params.addValue("startTime", query.startTime());
    }
    if (query.endTime() != null) {
      where.append(" AND started_at <= :endTime");
      params.addValue("endTime", query.endTime());
    }
    return new QuerySpec(where.toString(), params);
  }

  private void addEquals(
      StringBuilder where,
      MapSqlParameterSource params,
      String column,
      String parameter,
      String value) {
    if (value == null) return;
    where.append(" AND ").append(column).append(" = :").append(parameter);
    params.addValue(parameter, value);
  }

  private AuditOperationSummary operationSummary(ResultSet resultSet) throws SQLException {
    LocalDateTime startedAt = localDateTime(resultSet, "started_at");
    LocalDateTime finishedAt = localDateTime(resultSet, "finished_at");
    return new AuditOperationSummary(
        resultSet.getString("operation_id"),
        resultSet.getString("operation_type"),
        resultSet.getString("operation_name"),
        resultSet.getString("actor_id"),
        resultSet.getString("actor_name"),
        nullableLong(resultSet, "project_id"),
        resultSet.getString("project_name"),
        resultSet.getString("resource_type"),
        resultSet.getString("resource_id"),
        resultSet.getString("resource_name"),
        resultSet.getString("status"),
        resultSet.getString("source"),
        startedAt,
        finishedAt,
        startedAt == null || finishedAt == null
            ? null
            : Duration.between(startedAt, finishedAt).toMillis(),
        resultSet.getString("root_trace_id"),
        resultSet.getString("error_code"),
        resultSet.getString("summary"));
  }

  private AuditTimelineEvent timelineEvent(ResultSet resultSet) throws SQLException {
    Map<String, Object> payload = jsonMap(resultSet.getString("payload_json"));
    String eventType = resultSet.getString("event_type");
    String eventStatus = resultSet.getString("event_status");
    String reasonCode = resultSet.getString("reason_code");
    String message = resultSet.getString("message");
    AuditEventPresentation presentation =
        rendererRegistry.render(eventType, eventStatus, reasonCode, message, payload);
    return new AuditTimelineEvent(
        resultSet.getLong("id"),
        eventType,
        resultSet.getString("event_category"),
        eventStatus,
        localDateTime(resultSet, "occurred_at"),
        resultSet.getString("actor_id"),
        resultSet.getString("resource_type"),
        resultSet.getString("resource_id"),
        resultSet.getString("trace_id"),
        resultSet.getString("span_id"),
        nullableLong(resultSet, "parent_event_id"),
        reasonCode,
        message,
        presentation.title(),
        presentation.description(),
        payload);
  }

  private List<AuditFilterOption> simpleDistinctOptions(String column) {
    String safeColumn =
        switch (column) {
          case "operation_type", "resource_type", "status", "source" -> column;
          default ->
              throw new IllegalArgumentException("Unsupported audit option column: " + column);
        };
    return optionQuery(
        "SELECT DISTINCT "
            + safeColumn
            + " AS option_value, "
            + safeColumn
            + " AS option_label FROM yak_audit_operation WHERE "
            + safeColumn
            + " IS NOT NULL AND "
            + safeColumn
            + " <> '' ORDER BY option_label ASC");
  }

  private List<AuditFilterOption> optionQuery(String sql) {
    return jdbcTemplate.query(
        sql,
        (resultSet, rowNum) ->
            new AuditFilterOption(
                resultSet.getString("option_value"),
                resultSet.getString("option_label")));
  }

  private Map<String, Object> jsonMap(String value) {
    if (value == null || value.isBlank()) return Map.of();
    try {
      Map<String, Object> parsed = objectMapper.readValue(value, MAP_TYPE);
      return parsed == null ? Map.of() : parsed;
    } catch (Exception exception) {
      log.debug("Unable to parse persisted audit JSON", exception);
      return Map.of();
    }
  }

  private LocalDateTime localDateTime(ResultSet resultSet, String column) throws SQLException {
    Timestamp value = resultSet.getTimestamp(column);
    return value == null ? null : value.toLocalDateTime();
  }

  private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
    Number value = (Number) resultSet.getObject(column);
    return value == null ? null : value.longValue();
  }

  private String normalize(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private record QuerySpec(String where, MapSqlParameterSource params) {}

  private record OperationRow(
      AuditOperationSummary summary, Map<String, Object> metadata) {}
}
