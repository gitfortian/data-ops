package io.yak.ops.business.sync.offline.engine.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.engine.ConnectorIdResolver;
import io.yak.ops.business.sync.offline.engine.LinkUpJobSpecFactory;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.Role;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapterRegistry;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Chooses one durable execution shape without teaching the editor database-specific behavior.
 *
 * <p>GUIDE_SINGLE remains one Link-Up JobSpec. GUIDE_MULTI remains a native multi-table JobSpec only
 * when the complete source/sink adapter pair can translate it. Every other explicit multi-table
 * selection is frozen as FAN_OUT child single-table JobSpecs.</p>
 */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineExecutionPlanFactory {

  private final LinkUpJobSpecFactory jobSpecFactory;
  private final OfflineSyncConnectorAdapterRegistry adapterRegistry;
  private final OfflineExecutionPlanCodec planCodec;
  private final ObjectMapper objectMapper;

  @Autowired
  public OfflineExecutionPlanFactory(
      LinkUpJobSpecFactory jobSpecFactory,
      OfflineSyncConnectorAdapterRegistry adapterRegistry,
      OfflineExecutionPlanCodec planCodec,
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.jobSpecFactory = jobSpecFactory;
    this.adapterRegistry = adapterRegistry;
    this.planCodec = planCodec;
    this.objectMapper = objectMapper;
  }

  /** Convenience constructor for focused tests that only need the established JDBC adapter set. */
  public OfflineExecutionPlanFactory(
      LinkUpJobSpecFactory jobSpecFactory,
      ObjectMapper objectMapper) {
    this(
        jobSpecFactory,
        OfflineSyncConnectorAdapterRegistry.standard(objectMapper),
        new OfflineExecutionPlanCodec(objectMapper),
        objectMapper);
  }

  public BuildResult build(JsonNode definition) {
    requireObject(definition, "任务定义不能为空");
    String mode = text(definition.path("basic"), "mode", "GUIDE_SINGLE");
    if (!"GUIDE_SINGLE".equals(mode) && !"GUIDE_MULTI".equals(mode)) {
      throw new IllegalArgumentException("离线同步仅支持 GUIDE_SINGLE 和 GUIDE_MULTI 模式");
    }

    if ("GUIDE_SINGLE".equals(mode)) {
      return direct(jobSpecFactory.build(definition), OfflineExecutionStrategy.SINGLE);
    }

    String sourceConnectorId = connectorId(definition.path("source"), "jdbc");
    String sinkConnectorId = connectorId(definition.path("sink"), "jdbc");
    OfflineSyncConnectorAdapter sourceAdapter =
        adapterRegistry.resolve(sourceConnectorId, Role.SOURCE);
    OfflineSyncConnectorAdapter sinkAdapter =
        adapterRegistry.resolve(sinkConnectorId, Role.SINK);

    boolean nativeMulti =
        sourceAdapter.supportsNativeMultiTable(sourceConnectorId, Role.SOURCE)
            && sinkAdapter.supportsNativeMultiTable(sinkConnectorId, Role.SINK);
    if (nativeMulti) {
      return direct(jobSpecFactory.build(definition), OfflineExecutionStrategy.NATIVE_MULTI);
    }
    return fanOut(definition, sourceConnectorId, sinkConnectorId);
  }

  private BuildResult fanOut(
      JsonNode definition,
      String sourceConnectorId,
      String sinkConnectorId) {
    List<String> sourceTables = sourceTables(endpointConfig(definition, "source"));
    String targetTemplate =
        requiredText(
            endpointConfig(definition, "sink"),
            "targetTableName",
            "多表 FAN_OUT 缺少目标表命名模板");

    List<OfflineExecutionPlan.Unit> units = new ArrayList<>();
    List<String> targetTables = new ArrayList<>();
    LinkUpJobSpecFactory.BuildResult first = null;

    for (int index = 0; index < sourceTables.size(); index++) {
      String sourceTable = sourceTables.get(index);
      String sinkTable = renderTargetTable(targetTemplate, sourceTable);
      JsonNode childDefinition = singleTableDefinition(definition, sourceTable, sinkTable);
      LinkUpJobSpecFactory.BuildResult child = jobSpecFactory.build(childDefinition);

      if (!sourceConnectorId.equalsIgnoreCase(child.getSourceConnectorId())
          || !sinkConnectorId.equalsIgnoreCase(child.getSinkConnectorId())) {
        throw new IllegalStateException("FAN_OUT child connector identity drifted from frozen definition");
      }
      if (first == null) {
        first = child;
      }

      String unitKey = String.format(Locale.ROOT, "%04d", index + 1);
      units.add(
          new OfflineExecutionPlan.Unit(
              unitKey,
              sourceTable,
              sinkTable,
              child.getJobSpec()));
      targetTables.add(sinkTable);
    }

    if (first == null) {
      throw new IllegalStateException("FAN_OUT execution plan contains no child JobSpec");
    }

    OfflineExecutionPlan plan =
        new OfflineExecutionPlan(OfflineExecutionStrategy.FAN_OUT, units);
    JsonNode encoded = planCodec.encode(plan);
    return new BuildResult(
        encoded,
        planCodec.encodeJson(plan),
        first.getSourceDataSource(),
        first.getSinkDataSource(),
        sourceConnectorId,
        sinkConnectorId,
        write(sourceTables),
        write(targetTables),
        OfflineExecutionStrategy.FAN_OUT);
  }

  private BuildResult direct(
      LinkUpJobSpecFactory.BuildResult result,
      OfflineExecutionStrategy strategy) {
    return new BuildResult(
        result.getJobSpec(),
        result.getJobSpecJson(),
        result.getSourceDataSource(),
        result.getSinkDataSource(),
        result.getSourceConnectorId(),
        result.getSinkConnectorId(),
        result.getSourceTable(),
        result.getSinkTable(),
        strategy);
  }

  private JsonNode singleTableDefinition(
      JsonNode definition,
      String sourceTable,
      String sinkTable) {
    ObjectNode child = (ObjectNode) definition.deepCopy();
    child.with("basic").put("mode", "GUIDE_SINGLE");

    ObjectNode sourceConfig = requireObjectNode(
        child.path("source").path("config"), "来源端 config 不能为空");
    sourceConfig.put("table", sourceTable);
    sourceConfig.remove(List.of("tables", "tablePattern"));
    removeMultiTableOption(sourceConfig);

    ObjectNode sinkConfig = requireObjectNode(
        child.path("sink").path("config"), "目标端 config 不能为空");
    sinkConfig.put("targetTableName", sinkTable);
    removeMultiTableOption(sinkConfig);
    return child;
  }

  private void removeMultiTableOption(ObjectNode config) {
    JsonNode value = config.get("connectorOptions");
    if (value != null && value.isObject()) {
      ((ObjectNode) value).remove("table_list");
    }
  }

  private List<String> sourceTables(JsonNode config) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    flattenTables(config.path("tables"), null, result);
    if (result.isEmpty()) {
      String pattern = text(config, "tablePattern", null);
      if (StringUtils.hasText(pattern) && !containsWildcard(pattern)) {
        result.add(pattern.trim());
      }
    }
    if (result.isEmpty()) {
      throw new IllegalArgumentException(
          "多表 FAN_OUT 必须冻结至少一张明确的来源表，当前阶段不直接执行通配符表名");
    }
    return new ArrayList<>(result);
  }

  private void flattenTables(JsonNode value, String namespace, Set<String> result) {
    if (value == null || value.isNull() || value.isMissingNode()) {
      return;
    }
    if (value.isTextual()) {
      String table = value.asText().trim();
      if (StringUtils.hasText(table)) {
        result.add(
            StringUtils.hasText(namespace) && !table.contains(".")
                ? namespace + "." + table
                : table);
      }
      return;
    }
    if (value.isArray()) {
      value.forEach(item -> flattenTables(item, namespace, result));
      return;
    }
    if (value.isObject()) {
      value.fields().forEachRemaining(
          entry -> flattenTables(entry.getValue(), entry.getKey(), result));
    }
  }

  private String renderTargetTable(String template, String sourceTable) {
    int separator = sourceTable.lastIndexOf('.');
    String namespace = separator < 0 ? "" : sourceTable.substring(0, separator);
    String table = separator < 0 ? sourceTable : sourceTable.substring(separator + 1);
    return template
        .replace("${schema_name}", namespace)
        .replace("${table_name}", table);
  }

  private JsonNode endpointConfig(JsonNode definition, String endpointName) {
    JsonNode endpoint = definition.get(endpointName);
    requireObject(endpoint, endpointName + " 配置不能为空");
    JsonNode config = endpoint.get("config");
    requireObject(config, endpointName + " config 不能为空");
    return config;
  }

  private String connectorId(JsonNode endpoint, String fallback) {
    JsonNode config = endpoint.path("config");
    return ConnectorIdResolver.resolve(
        text(endpoint, "connectorId", text(config, "connectorId", null)),
        text(endpoint, "connectorType", text(config, "connectorType", null)),
        text(endpoint, "dbType", text(config, "dbType", null)),
        fallback);
  }

  private String requiredText(JsonNode node, String field, String message) {
    String value = text(node, field, null);
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private String text(JsonNode node, String field, String fallback) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull() || !value.isValueNode()) {
      return fallback;
    }
    return value.asText(fallback);
  }

  private boolean containsWildcard(String value) {
    return value.contains("*") || value.contains("?") || value.contains("[");
  }

  private void requireObject(JsonNode node, String message) {
    if (node == null || !node.isObject()) {
      throw new IllegalArgumentException(message);
    }
  }

  private ObjectNode requireObjectNode(JsonNode node, String message) {
    requireObject(node, message);
    return (ObjectNode) node;
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化多表信息失败", exception);
    }
  }

  public static final class BuildResult {
    private final JsonNode logicalSpec;
    private final String logicalSpecJson;
    private final DataSourcePO sourceDataSource;
    private final DataSourcePO sinkDataSource;
    private final String sourceConnectorId;
    private final String sinkConnectorId;
    private final String sourceTable;
    private final String sinkTable;
    private final OfflineExecutionStrategy strategy;

    BuildResult(
        JsonNode logicalSpec,
        String logicalSpecJson,
        DataSourcePO sourceDataSource,
        DataSourcePO sinkDataSource,
        String sourceConnectorId,
        String sinkConnectorId,
        String sourceTable,
        String sinkTable,
        OfflineExecutionStrategy strategy) {
      this.logicalSpec = logicalSpec;
      this.logicalSpecJson = logicalSpecJson;
      this.sourceDataSource = sourceDataSource;
      this.sinkDataSource = sinkDataSource;
      this.sourceConnectorId = sourceConnectorId;
      this.sinkConnectorId = sinkConnectorId;
      this.sourceTable = sourceTable;
      this.sinkTable = sinkTable;
      this.strategy = strategy;
    }

    public JsonNode getLogicalSpec() {
      return logicalSpec;
    }

    public String getLogicalSpecJson() {
      return logicalSpecJson;
    }

    public DataSourcePO getSourceDataSource() {
      return sourceDataSource;
    }

    public DataSourcePO getSinkDataSource() {
      return sinkDataSource;
    }

    public String getSourceConnectorId() {
      return sourceConnectorId;
    }

    public String getSinkConnectorId() {
      return sinkConnectorId;
    }

    public String getSourceTable() {
      return sourceTable;
    }

    public String getSinkTable() {
      return sinkTable;
    }

    public OfflineExecutionStrategy getStrategy() {
      return strategy;
    }
  }
}
