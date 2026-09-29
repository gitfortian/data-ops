package io.yak.ops.business.sync.offline.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.ExecutionContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.Role;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapterRegistry;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 离线同步定义与 Link-Up JobSpec 转换工厂。
 *
 * <p>新定义使用固定的 basic + source + sink + channel 结构。读取历史版本时仍兼容
 * workflow.nodes 与 env，避免旧任务版本在执行或回看时失效。</p>
 *
 * <p>Connector-specific task options and datasource translation are delegated to
 * {@link OfflineSyncConnectorAdapter}. This factory only owns definition parsing, runtime
 * orchestration and canonical JobSpec assembly.</p>
 *
 * @author weifuwan
 */
@ConditionalOnOfflineSyncEnabled
@Component
public class LinkUpJobSpecFactory {

  private static final String API_VERSION = "link-up/v1";
  private static final String KIND = "BatchSyncJob";

  private final DataSourceDao dataSourceDao;
  private final ObjectMapper objectMapper;
  private final OfflineSyncConnectorAdapterRegistry adapterRegistry;

  @Autowired
  public LinkUpJobSpecFactory(
      DataSourceDao dataSourceDao,
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper,
      OfflineSyncConnectorAdapterRegistry adapterRegistry) {
    this.dataSourceDao = dataSourceDao;
    this.objectMapper = objectMapper;
    this.adapterRegistry = adapterRegistry;
  }

  /** Keeps focused unit tests and historical direct construction source-compatible. */
  public LinkUpJobSpecFactory(
      DataSourceDao dataSourceDao,
      ObjectMapper objectMapper) {
    this(
        dataSourceDao,
        objectMapper,
        OfflineSyncConnectorAdapterRegistry.standard(objectMapper));
  }

  /** Builds a logical, durable JobSpec. Datasource credentials are intentionally not included. */
  public BuildResult build(JsonNode definition) {
    requireObject(definition, "任务定义不能为空");
    JsonNode basic = definition.path("basic");
    requireObject(basic, "basic 配置不能为空");

    String jobName = requiredText(basic, "jobName", "任务名称不能为空");
    String mode = text(basic, "mode", text(definition, "mode", "GUIDE_SINGLE"));
    if (!"GUIDE_SINGLE".equals(mode) && !"GUIDE_MULTI".equals(mode)) {
      throw new IllegalArgumentException("离线同步仅支持 GUIDE_SINGLE 和 GUIDE_MULTI 模式");
    }

    Endpoint source = endpoint(definition, "source");
    Endpoint sink = endpoint(definition, "sink");
    JsonNode channel = channel(definition);
    String sourceConnectorId = connectorId(source, "jdbc");
    String sinkConnectorId = connectorId(sink, "jdbc");

    OfflineSyncConnectorAdapter sourceAdapter =
        adapterRegistry.resolve(sourceConnectorId, Role.SOURCE);
    OfflineSyncConnectorAdapter sinkAdapter =
        adapterRegistry.resolve(sinkConnectorId, Role.SINK);

    Long sourceDataSourceId =
        resolveDataSourceId(
            source,
            definition,
            true,
            sourceAdapter.requiresDataSource(sourceConnectorId, Role.SOURCE));
    Long sinkDataSourceId =
        resolveDataSourceId(
            sink,
            definition,
            false,
            sinkAdapter.requiresDataSource(sinkConnectorId, Role.SINK));

    DataSourcePO sourceDataSource = dataSource(sourceDataSourceId, "来源端");
    DataSourcePO sinkDataSource = dataSource(sinkDataSourceId, "目标端");

    int parallelism =
        Math.max(
            1,
            intValue(
                channel,
                "parallelism",
                definition.path("env").path("parallelism").asInt(1)));
    int channelCapacity =
        Math.max(
            1,
            intValue(
                channel,
                "channelCapacity",
                definition.path("env").path("channelCapacity").asInt(64)));
    int batchSize = Math.max(1, sink.config.path("batchSize").asInt(1000));
    int fetchSize = Math.max(1, source.config.path("fetchSize").asInt(batchSize));

    OfflineSyncConnectorAdapter.BuildResult sourceBuild =
        sourceAdapter.build(
            new BuildContext(
                sourceConnectorId,
                Role.SOURCE,
                mode,
                jobName,
                source.config,
                channel,
                connectorOptions(source.config),
                batchSize,
                fetchSize,
                List.of()));

    OfflineSyncConnectorAdapter.BuildResult sinkBuild =
        sinkAdapter.build(
            new BuildContext(
                sinkConnectorId,
                Role.SINK,
                mode,
                jobName,
                sink.config,
                channel,
                connectorOptions(sink.config),
                batchSize,
                fetchSize,
                sourceBuild.sourceTables()));

    ObjectNode runtime = objectMapper.createObjectNode();
    runtime.put("batchSize", fetchSize);
    runtime.put("sourceParallelism", parallelism);
    runtime.put("sinkParallelism", parallelism);
    runtime.put("pipelineParallelism", "GUIDE_MULTI".equals(mode) ? parallelism : 1);
    runtime.put("maxBufferedBatches", channelCapacity);
    if (channel.path("speedLimitEnabled").asBoolean(false)) {
      runtime.put(
          "maxRecordsPerSecond",
          Math.max(1L, longValue(channel, "recordsPerSecond", 10000L)));
    }
    copyRuntime(definition.path("env"), runtime);
    copyRuntime(channel, runtime);

    ObjectNode jobSpec = objectMapper.createObjectNode();
    jobSpec.put("apiVersion", API_VERSION);
    jobSpec.put("kind", KIND);
    jobSpec.put("name", jobName.trim());
    jobSpec.set(
        "source",
        connector(sourceConnectorId, sourceBuild.options(), sourceDataSourceId));
    jobSpec.set(
        "sink",
        connector(sinkConnectorId, sinkBuild.options(), sinkDataSourceId));
    jobSpec.set("runtime", runtime);

    JsonNode canonical = canonical(jobSpec);
    return new BuildResult(
        canonical,
        write(canonical),
        sourceDataSource,
        sinkDataSource,
        sourceConnectorId,
        sinkConnectorId,
        sourceBuild.tableView(),
        sinkBuild.tableView());
  }

  /** Resolves datasource references immediately before a Worker submission. */
  public JsonNode resolveForExecution(JsonNode logicalJobSpec) {
    requireObject(logicalJobSpec, "JobSpec 不能为空");
    ObjectNode resolved = (ObjectNode) logicalJobSpec.deepCopy();
    resolveEndpointForExecution(resolved, "source", "来源端", Role.SOURCE);
    resolveEndpointForExecution(resolved, "sink", "目标端", Role.SINK);
    return canonical(resolved);
  }

  public String resolveForExecution(String logicalJobSpecJson) {
    if (!StringUtils.hasText(logicalJobSpecJson)) {
      throw new IllegalArgumentException("JobSpec 不能为空");
    }
    try {
      return write(resolveForExecution(objectMapper.readTree(logicalJobSpecJson)));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("JobSpec JSON 已损坏", exception);
    }
  }

  private void resolveEndpointForExecution(
      ObjectNode root,
      String endpointName,
      String endpointLabel,
      Role role) {
    JsonNode value = root.get(endpointName);
    requireObject(value, endpointLabel + " JobSpec 不完整");
    ObjectNode endpoint = (ObjectNode) value;

    String connectorId =
        ConnectorIdResolver.resolve(text(endpoint, "connectorId", null), null, null, null);
    endpoint.put("connectorId", connectorId);

    OfflineSyncConnectorAdapter adapter = adapterRegistry.resolve(connectorId, role);
    DataSourcePO dataSource = null;
    if (adapter.requiresDataSource(connectorId, role)) {
      long dataSourceId = endpoint.path("dataSourceRef").path("id").asLong(0L);
      if (dataSourceId <= 0L) {
        String connectorLabel =
            "jdbc".equalsIgnoreCase(connectorId) ? "JDBC" : connectorId;
        throw new IllegalStateException(
            endpointLabel + " " + connectorLabel + " JobSpec 缺少 dataSourceRef.id");
      }
      dataSource = dataSource(dataSourceId, endpointLabel);
    }

    ObjectNode options = endpoint.with("options");
    adapter.resolveForExecution(
        new ExecutionContext(connectorId, role, endpointLabel, dataSource, options));
    endpoint.remove("dataSourceRef");
  }

  private ObjectNode connector(
      String connectorId,
      ObjectNode options,
      Long dataSourceId) {
    ObjectNode connector = objectMapper.createObjectNode();
    connector.put("connectorId", connectorId);
    if (dataSourceId != null) {
      connector.putObject("dataSourceRef").put("id", dataSourceId);
    }
    connector.set("options", options);
    return connector;
  }

  private String connectorId(Endpoint endpoint, String fallback) {
    return ConnectorIdResolver.resolve(
        text(endpoint.root, "connectorId", text(endpoint.config, "connectorId", null)),
        text(endpoint.root, "connectorType", text(endpoint.config, "connectorType", null)),
        text(endpoint.root, "dbType", text(endpoint.config, "dbType", null)),
        fallback);
  }

  private ObjectNode connectorOptions(JsonNode config) {
    JsonNode value = config == null ? null : config.get("connectorOptions");
    return value != null && value.isObject()
        ? (ObjectNode) value.deepCopy()
        : objectMapper.createObjectNode();
  }

  private void copyRuntime(JsonNode source, ObjectNode runtime) {
    copyPositiveLong(source, runtime, "maxBufferedRecords", "maxBufferedRecords");
    copyPositiveLong(source, runtime, "maxBufferedBytes", "maxBufferedBytes");
    copyPositiveLong(source, runtime, "maxRecordsPerSecond", "maxRecordsPerSecond");
    copyPositiveLong(source, runtime, "maxBytesPerSecond", "maxBytesPerSecond");
    copyText(source, runtime, "sinkPartitionStrategy", "sinkPartitionStrategy");
    copyText(source, runtime, "splitAssignmentMode", "splitAssignmentMode");
  }

  private void copyPositiveLong(
      JsonNode source,
      ObjectNode target,
      String sourceKey,
      String targetKey) {
    JsonNode value = source == null ? null : source.get(sourceKey);
    if (value != null && value.isNumber() && value.asLong() > 0L) {
      target.put(targetKey, value.asLong());
    }
  }

  private void copyText(
      JsonNode source,
      ObjectNode target,
      String sourceKey,
      String targetKey) {
    String value = text(source, sourceKey, null);
    if (StringUtils.hasText(value)) {
      target.put(targetKey, value.trim());
    }
  }

  private JsonNode canonical(JsonNode node) {
    if (node == null || node.isNull() || node.isValueNode()) {
      return node;
    }
    if (node.isArray()) {
      ArrayNode result = objectMapper.createArrayNode();
      for (JsonNode item : node) {
        result.add(canonical(item));
      }
      return result;
    }
    ObjectNode result = objectMapper.createObjectNode();
    Map<String, JsonNode> fields = new TreeMap<>();
    Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
    while (iterator.hasNext()) {
      Map.Entry<String, JsonNode> entry = iterator.next();
      fields.put(entry.getKey(), canonical(entry.getValue()));
    }
    fields.forEach(result::set);
    return result;
  }

  private Endpoint endpoint(JsonNode definition, String kind) {
    JsonNode direct = definition.get(kind);
    if (direct != null && direct.isObject()) {
      JsonNode config = direct.get("config");
      if (config == null || config.isNull() || config.isMissingNode()) {
        config = objectMapper.createObjectNode();
      }
      requireObject(config, kind + " 配置必须是 JSON 对象");
      return new Endpoint(direct, config);
    }

    JsonNode workflow = definition.path("workflow");
    JsonNode nodes = workflow.path("nodes");
    if (!nodes.isArray()) {
      throw new IllegalArgumentException("任务缺少 " + kind + " 配置");
    }
    for (JsonNode node : nodes) {
      JsonNode data = node.path("data");
      String nodeType = text(data, "nodeType", text(node, "type", null));
      if (kind.equalsIgnoreCase(nodeType)) {
        JsonNode config = data.path("config");
        requireObject(config, kind + " 节点配置不能为空");
        return new Endpoint(data, config);
      }
    }
    throw new IllegalArgumentException("任务缺少 " + kind + " 配置");
  }

  private JsonNode channel(JsonNode definition) {
    JsonNode direct = definition.get("channel");
    if (direct != null && direct.isObject()) {
      return direct;
    }
    JsonNode legacy = definition.path("workflow").path("channelConfig");
    return legacy.isObject() ? legacy : objectMapper.createObjectNode();
  }

  private Long resolveDataSourceId(
      Endpoint endpoint,
      JsonNode definition,
      boolean source,
      boolean required) {
    long id = longValue(endpoint.root, "dataSourceId", 0L);
    if (id <= 0L) {
      id = longValue(endpoint.config, "dataSourceId", 0L);
    }
    if (id <= 0L) {
      id =
          longValue(
              definition.path("basic"),
              source ? "sourceDataSourceId" : "targetDataSourceId",
              0L);
    }
    if (id <= 0L) {
      id =
          longValue(
              definition.path("workflow"),
              source ? "sourceDataSourceId" : "targetDataSourceId",
              0L);
    }
    if (id <= 0L && required) {
      throw new IllegalArgumentException(source ? "请选择来源数据源" : "请选择目标数据源");
    }
    return id <= 0L ? null : id;
  }

  private DataSourcePO dataSource(Long id, String endpointName) {
    if (id == null) {
      return null;
    }
    DataSourcePO dataSource = dataSourceDao.selectById(id);
    if (dataSource == null) {
      throw new IllegalArgumentException(endpointName + "数据源不存在：" + id);
    }
    return dataSource;
  }

  private String write(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化 JobSpec 失败", exception);
    }
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

  private int intValue(JsonNode node, String field, int fallback) {
    long value = longValue(node, field, fallback);
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
  }

  private long longValue(JsonNode node, String field, long fallback) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull() || !value.isValueNode()) {
      return fallback;
    }
    if (value.isNumber()) {
      return value.asLong(fallback);
    }
    String text = value.asText("").trim();
    if (!StringUtils.hasText(text)) {
      return fallback;
    }
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private void requireObject(JsonNode node, String message) {
    if (node == null || !node.isObject()) {
      throw new IllegalArgumentException(message);
    }
  }

  private static final class Endpoint {
    private final JsonNode root;
    private final JsonNode config;

    private Endpoint(JsonNode root, JsonNode config) {
      this.root = root;
      this.config = config;
    }
  }

  public static final class BuildResult {
    private final JsonNode jobSpec;
    private final String jobSpecJson;
    private final DataSourcePO sourceDataSource;
    private final DataSourcePO sinkDataSource;
    private final String sourceConnectorId;
    private final String sinkConnectorId;
    private final String sourceTable;
    private final String sinkTable;

    BuildResult(
        JsonNode jobSpec,
        String jobSpecJson,
        DataSourcePO sourceDataSource,
        DataSourcePO sinkDataSource,
        String sourceConnectorId,
        String sinkConnectorId,
        String sourceTable,
        String sinkTable) {
      this.jobSpec = jobSpec;
      this.jobSpecJson = jobSpecJson;
      this.sourceDataSource = sourceDataSource;
      this.sinkDataSource = sinkDataSource;
      this.sourceConnectorId = sourceConnectorId;
      this.sinkConnectorId = sinkConnectorId;
      this.sourceTable = sourceTable;
      this.sinkTable = sinkTable;
    }

    public JsonNode getJobSpec() {
      return jobSpec;
    }

    public String getJobSpecJson() {
      return jobSpecJson;
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
  }
}
