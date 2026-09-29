package io.yak.ops.business.sync.offline.engine.connector.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.engine.connector.adapter.NativeSingleTableSupport.TableRef;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Translates GUIDE_SINGLE tasks to the Link-Up Doris native Source / Stream Load Sink. */
@ConditionalOnOfflineSyncEnabled
@Component
public class DorisOfflineSyncConnectorAdapter implements OfflineSyncConnectorAdapter {

  private static final String INTERNAL_AUTO_CREATE = "_yak_ops_auto_create_table";
  private final ObjectMapper objectMapper;
  private final ObjectProvider<DataSourceCatalogReader> catalogReaderProvider;

  public DorisOfflineSyncConnectorAdapter(
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper,
      ObjectProvider<DataSourceCatalogReader> catalogReaderProvider) {
    this.objectMapper = objectMapper;
    this.catalogReaderProvider = catalogReaderProvider;
  }

  @Override
  public boolean supports(String connectorId, Role role) {
    return "doris".equalsIgnoreCase(connectorId);
  }

  @Override
  public boolean requiresDataSource(String connectorId, Role role) {
    return true;
  }

  @Override
  public BuildResult build(BuildContext context) {
    NativeSingleTableSupport.requireSingle(context);
    ObjectNode options = context.options();
    removeDatasourceOwned(options);

    if (context.role() == Role.SOURCE) {
      String sourceTable = NativeSingleTableSupport.sourceTable(context.config());
      TableRef table = NativeSingleTableSupport.tableRef(sourceTable);
      if (StringUtils.hasText(table.database())) options.put("database", table.database());
      options.put("table", table.table());
      String filter = NativeSingleTableSupport.stripWhere(
          NativeSingleTableSupport.text(context.config(), "whereCondition", null));
      if (StringUtils.hasText(filter)) options.put("doris.filter.query", filter);
      options.put("doris.batch.size", context.fetchSize());
      return new BuildResult(options, sourceTable, List.of(sourceTable));
    }

    String sinkTable = NativeSingleTableSupport.sinkTable(context.config());
    TableRef table = NativeSingleTableSupport.tableRef(sinkTable);
    if (StringUtils.hasText(table.database())) options.put("database", table.database());
    options.put("table", table.table());
    options.put("doris.batch.size", context.batchSize());
    options.put(
        INTERNAL_AUTO_CREATE,
        context.config().path("autoCreateTable").asBoolean(false));

    String writeMode =
        NativeSingleTableSupport.text(context.config(), "writeMode", "append").toLowerCase();
    if ("overwrite".equals(writeMode)) {
      throw new IllegalArgumentException("Doris Native Sink 当前不支持覆盖写入，请使用 Append 或 Upsert");
    }
    options.put("sink.key-type", "upsert".equals(writeMode) ? "UNIQUE" : "DUPLICATE");
    return new BuildResult(options, sinkTable, List.of());
  }

  @Override
  public void resolveForExecution(ExecutionContext context) {
    JsonNode dataSource = NativeSingleTableSupport.dataSource(objectMapper, context.dataSource());
    ObjectNode options = context.options();
    removeDatasourceOwned(options);

    String fenodes = NativeSingleTableSupport.firstText(dataSource, "fenodes");
    if (!StringUtils.hasText(fenodes)) {
      String host = NativeSingleTableSupport.firstText(dataSource, "host", "hostname");
      if (StringUtils.hasText(host)) {
        fenodes = host + ":8030";
      }
    }
    if (!StringUtils.hasText(fenodes)) {
      throw new IllegalArgumentException(
          "Doris Native Connector 无法解析 FE HTTP 节点，请在数据源配置 fenodes");
    }
    options.put("fenodes", fenodes);
    options.put("query-port", NativeSingleTableSupport.firstInt(dataSource, 9030, "port"));
    options.put("username", NativeSingleTableSupport.username(dataSource));
    options.put("password", NativeSingleTableSupport.password(dataSource));
    if (!StringUtils.hasText(options.path("database").asText(null))) {
      options.put(
          "database",
          NativeSingleTableSupport.requireText(
              NativeSingleTableSupport.database(dataSource), "Doris 数据源缺少 database"));
    }

    if (context.role() == Role.SINK) {
      boolean autoCreate = options.path(INTERNAL_AUTO_CREATE).asBoolean(false);
      options.remove(INTERNAL_AUTO_CREATE);
      if (!autoCreate) {
        verifyExistingTarget(context.dataSource(), options);
      }
    } else {
      options.remove(INTERNAL_AUTO_CREATE);
    }
  }

  private void verifyExistingTarget(DataSourcePO dataSource, ObjectNode options) {
    DataSourceCatalogReader catalogReader = catalogReaderProvider.getIfAvailable();
    if (catalogReader == null) {
      throw new IllegalStateException("Doris Native Sink 无法校验目标表是否存在：DataSource Catalog 未启用");
    }
    String database = options.path("database").asText();
    String table = options.path("table").asText();
    boolean exists = catalogReader.listTables(dataSource.getId(), database, null, table).stream()
        .anyMatch(value -> table.equalsIgnoreCase(value.name()));
    if (!exists) {
      throw new IllegalArgumentException(
          "目标表 " + database + "." + table + " 不存在；请开启自动创建目标表或先创建该表");
    }
  }

  private void removeDatasourceOwned(ObjectNode options) {
    options.remove(List.of("fenodes", "benodes", "query-port", "username", "password"));
  }
}
