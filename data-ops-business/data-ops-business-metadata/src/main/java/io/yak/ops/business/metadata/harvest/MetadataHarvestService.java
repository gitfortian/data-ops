package io.yak.ops.business.metadata.harvest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metadata.dao.mapper.MdCollectRunMapper;
import io.yak.ops.business.metadata.dao.model.CatalogAssetRow;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.BatchCommand;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.HarvestBatchResult;
import io.yak.ops.business.metadata.harvest.stats.MetadataStatsProvider.TableStats;
import io.yak.ops.business.metadata.harvest.stats.MetadataStatsRegistry;
import io.yak.ops.business.metadata.metamodel.MetadataKeyCodec;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry.TypeDefinition;
import io.yak.ops.business.metadata.dao.model.MdCollectJobPO;
import io.yak.ops.business.metadata.dao.model.MdCollectRunPO;
import io.yak.ops.common.enums.metadata.MetadataEntityStatus;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import io.yak.ops.common.enums.metadata.MetadataEnums.RunStatus;
import io.yak.ops.common.enums.metadata.MetadataEnums.TriggerType;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.common.util.metadata.PhysicalTableAssetKey;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 一轮物理采集：SPI 目录 → 四类实体行 → 唯一 upsert（plan §3.1/§3.2/§3.3）。
 *
 * <p><b>一次采集落四层</b>（{@code databaseService → database → table → tableColumn}），不是工单字面
 * 写的两层。理由只有一条：{@code parent_asset_id} 是 lineage 拥有的列、本模块<b>只在 INSERT 时写一次</b>
 * （steward 契约），所以层级一旦在第一轮没挂上就永久是平的，事后无列可补。多两层的代价是每轮四条语句。
 *
 * <p><b>分批口径</b>：SPI 的 {@code listTables} 没有游标，"≤500 分批"在这里只能落到
 * ①按作用域逐个调用（绝不做"一次拿全库清单"）+ ②内存里按 {@link #TABLE_CHUNK_SIZE} 张表一组
 * （列数约为表数 12.6 倍，一次驻留全集会把采集跑成内存问题）+ ③落库语句级 500 行一批
 * （{@link AssetUpsertRepository}）。
 *
 * <p><b>失败语义</b>：整轮任何异常 → 运行记 FAILED，<b>已写入的实体不回滚、一行都不删</b>。
 * 采集是幂等 upsert，回滚反而会把上一轮的好数据一起带走；缺的下一轮补齐。
 * 枚举跑完后交 {@link MetadataPresenceService} 判缺席（ticket 115）：四道闸任一拦下都不落 GONE，
 * 熔断那一道把运行状态改写成 SUSPECT——"这轮没删东西"本身就是要被看见的结论。
 *
 * <p><b>PARTIAL 是本类的核心诚实</b>：非 JDBC 插件读不到列（返回空集合或抛异常）时，这张表按
 * "在场但结构未知"处理——{@code content_hash} 留 NULL、不写 {@code columnCount}、不产列行，并把该作用域
 * 标记给 ticket 115 跳过列级 GONE。把"读不到"写成"没有列"，下一轮就会把全部列判成删除。
 */
@Slf4j
@Component
public class MetadataHarvestService {

  /** 一次驻留内存的表数；列行随之有界（列约为表的 12.6 倍）。 */
  static final int TABLE_CHUNK_SIZE = 50;

  /**
   * 本模块写入行的归属标签。
   *
   * <p>必须是<b>独立</b>值：lineage 的 {@code deleteUnreferencedOwnedAssets} 按
   * {@code (source_type, source_id)} 删除无关系、无子节点的资产。跟着血缘侧用 {@code DATASOURCE}
   * 就等于把自己的目录行交给别人的清理路径。
   *
   * <p>public 是因为登记通道（register 包）写同一张表必须用同一个标签，两处不同值即两套真相。
   */
  public static final String SOURCE_TYPE = "METADATA";

  static final String TYPE_SERVICE = "databaseService";
  static final String TYPE_DATABASE = "database";
  static final String TYPE_TABLE = "table";
  static final String TYPE_COLUMN = "tableColumn";

  private static final int NAME_MAX = 200;
  private static final int DISPLAY_NAME_MAX = 256;
  private static final int SUMMARY_MAX = 2048;
  private static final int FQN_MAX = 768;
  private static final int IDENTIFIER_MAX = 256;
  private static final int ERROR_MAX = 2000;
  private static final String NO_SCHEMA = "";

  private final HarvestCatalogSource catalogSource;
  private final MetadataTypeRegistry typeRegistry;
  private final MetadataAttributeCodec attributeCodec;
  private final AssetUpsertRepository upsertRepository;
  private final MetadataStatsRegistry statsRegistry;
  private final MetadataPresenceService presenceService;
  private final MdCollectRunMapper runMapper;
  private final ObjectMapper objectMapper;

  public MetadataHarvestService(
      HarvestCatalogSource catalogSource,
      MetadataTypeRegistry typeRegistry,
      MetadataAttributeCodec attributeCodec,
      AssetUpsertRepository upsertRepository,
      MetadataStatsRegistry statsRegistry,
      MetadataPresenceService presenceService,
      MdCollectRunMapper runMapper,
      ObjectMapper objectMapper) {
    this.catalogSource = catalogSource;
    this.typeRegistry = typeRegistry;
    this.attributeCodec = attributeCodec;
    this.upsertRepository = upsertRepository;
    this.statsRegistry = statsRegistry;
    this.presenceService = presenceService;
    this.runMapper = runMapper;
    this.objectMapper = objectMapper;
  }

  /**
   * 跑一轮采集。运行历史一定落行（dry-run 也落——它是"任务启用前置条件"的证据，plan §0.13），
   * 结论不回抛：调用方读 {@link HarvestSummary#status()} 判成败。
   */
  public HarvestSummary harvest(HarvestRequest request) {
    MdCollectJobPO job = request.job();
    if (job.getProjectId() == null) {
      throw new MetadataException(
          MetadataErrorCode.PROJECT_CONTEXT_REQUIRED, "采集任务未归属项目 job=" + job.getJobCode());
    }
    if (job.getDataSourceId() == null) {
      throw new MetadataException(
          MetadataErrorCode.COLLECT_JOB_SCOPE_INVALID,
          "物理采集任务必须绑定数据源 job=" + job.getJobCode());
    }
    LocalDateTime startedAt = LocalDateTime.now();
    MdCollectRunPO run = openRun(request, startedAt);
    Attempt attempt = new Attempt(request, run.getId(), startedAt);
    RuntimeException failure = null;
    try {
      attempt.execute();
      // 缺席判定只在枚举确实跑完之后进行：本轮没见过任何实体就判"它们消失了"，正是 plan §3.4 那条禁令。
      attempt.presence = presenceService.evaluateAndApply(attempt.presenceFacts());
    } catch (RuntimeException e) {
      failure = e;
      log.warn(
          "采集失败 job={} run={}：本轮已登记的实体不回滚、也不删任何行，缺的下一轮补齐",
          job.getJobCode(),
          run.getId(),
          e);
    }
    closeRun(run, attempt, failure);
    return attempt.summary(run.getId(), failure);
  }

  /** 表名通配：只认 {@code %} 与 {@code _}，其余字符一律按字面量（表名里带下划线是常态）。 */
  static boolean matchesPattern(String tableName, String pattern) {
    if (pattern == null || pattern.isBlank() || "%".equals(pattern.trim())) {
      return true;
    }
    StringBuilder regex = new StringBuilder("(?i)");
    for (char unit : pattern.trim().toCharArray()) {
      if (unit == '%') {
        regex.append(".*");
      } else if (unit == '_') {
        regex.append('.');
      } else {
        regex.append(Pattern.quote(String.valueOf(unit)));
      }
    }
    return Pattern.compile(regex.toString()).matcher(tableName).matches();
  }

  /** 与 {@code TableIdentityResolver.normalize} 同口径：trim + 小写，空值成空串而不是 null。 */
  static String normalizeIdentifier(String raw) {
    return raw == null || raw.isBlank() ? "" : raw.trim().toLowerCase(Locale.ROOT);
  }

  private MdCollectRunPO openRun(HarvestRequest request, LocalDateTime startedAt) {
    MdCollectJobPO job = request.job();
    MdCollectRunPO run = new MdCollectRunPO();
    run.setProjectId(job.getProjectId());
    run.setJobId(job.getId());
    run.setProviderType(ProviderType.HARVESTED.name());
    run.setTriggerType(request.triggerType().name());
    run.setDryRun(request.dryRun());
    run.setStatus(RunStatus.RUNNING.name());
    run.setCntTotal(0);
    run.setCntNew(0);
    run.setCntChanged(0);
    run.setCntUnchanged(0);
    run.setCntGone(0);
    run.setCntPartialFailed(0);
    run.setScopeSnapshot(scopeSnapshot(job));
    run.setStartedAt(startedAt);
    run.setCreatedBy(request.operator());
    runMapper.insert(run);
    return run;
  }

  private String scopeSnapshot(MdCollectJobPO job) {
    Map<String, Object> scope = new LinkedHashMap<>();
    scope.put("dataSourceId", job.getDataSourceId());
    scope.put("databaseName", job.getDatabaseName());
    scope.put("schemaName", job.getSchemaName());
    scope.put("tablePattern", job.getTablePattern());
    scope.put("collectColumns", job.getCollectColumns());
    try {
      return objectMapper.writeValueAsString(scope);
    } catch (Exception exception) {
      // 快照只是"任务改过配置后还能复盘"的辅助信息，不该因为它让一轮采集开不起来。
      log.warn("采集作用域快照序列化失败 job={}", job.getJobCode(), exception);
      return null;
    }
  }

  private void closeRun(MdCollectRunPO run, Attempt attempt, RuntimeException failure) {
    MetadataPresenceService.PresenceOutcome presence = attempt.presence;
    RunStatus status = RunStatus.SUCCESS;
    if (failure != null) {
      status = RunStatus.FAILED;
    } else if (presence != null && presence.suspect()) {
      // 熔断：本轮什么都没删，但"为什么没删"必须写在运行历史上，而不是只留在日志里。
      status = RunStatus.SUSPECT;
    }
    run.setStatus(status.name());
    run.setCntTotal(attempt.seen);
    run.setCntNew(attempt.newCount);
    run.setCntChanged(attempt.changedCount);
    run.setCntUnchanged(attempt.unchangedCount);
    run.setCntGone(presence == null ? 0 : presence.goneCount());
    run.setCntPartialFailed(attempt.partialFailed);
    run.setErrorMessage(
        failure == null ? presenceReason(presence) : truncate(message(failure), ERROR_MAX));
    run.setFinishedAt(LocalDateTime.now());
    run.setDurationMs(Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis());
    runMapper.updateById(run);
  }

  /** 没失败时 {@code error_message} 只装在场性结论；{@code null} = 这一轮没什么要交代的。 */
  private static String presenceReason(MetadataPresenceService.PresenceOutcome presence) {
    if (presence == null || presence.reason() == null) {
      return null;
    }
    return truncate(presence.reason(), ERROR_MAX);
  }

  private static String message(Throwable failure) {
    String text = failure.getMessage();
    return text == null || text.isBlank() ? failure.getClass().getSimpleName() : text;
  }

  private static String truncate(String raw, int max) {
    if (raw == null) {
      return null;
    }
    return raw.length() <= max ? raw : raw.substring(0, max);
  }

  private static String emptyToNull(String raw) {
    return raw == null || raw.isEmpty() ? null : raw;
  }

  private static String orNull(String raw) {
    return raw == null || raw.isBlank() ? null : raw.trim();
  }

  private static String orDefault(String raw, String fallback) {
    String value = orNull(raw);
    return value == null ? fallback : value;
  }

  private static <T> List<List<T>> partition(List<T> values, int size) {
    List<List<T>> parts = new ArrayList<>();
    for (int index = 0; index < values.size(); index += size) {
      parts.add(values.subList(index, Math.min(values.size(), index + size)));
    }
    return parts;
  }

  private static Map<String, String> fqnContext(
      String dataSourceName, String database, String schema, String table, String column) {
    Map<String, String> context = new LinkedHashMap<>();
    context.put("dataSourceName", orDefault(dataSourceName, ""));
    context.put("databaseName", orDefault(database, ""));
    context.put("schemaName", orDefault(schema, ""));
    context.put("tableName", orDefault(table, ""));
    context.put("columnName", orDefault(column, ""));
    return context;
  }

  /**
   * 一轮采集的可变现场。
   *
   * <p>本服务是单例，所以轮内状态一律放这里：计数器进实例字段会让两个项目的并发采集互相污染同一份运行历史。
   */
  private final class Attempt {

    private final HarvestRequest request;
    private final Long projectId;
    private final Long runId;
    private final LocalDateTime collectedAt;
    private final long dataSourceId;
    private final List<ScopeOutcome> scopes = new ArrayList<>();
    /** 本轮登记过的全部键（含 UNCHANGED）。空集 = "什么都没发现"，判 GONE 的第一道闸看它。 */
    private final Set<String> seenKeys = new LinkedHashSet<>();
    private final boolean collectColumns;
    private MetadataPresenceService.PresenceOutcome presence;
    private int seen;
    private int newCount;
    private int changedCount;
    private int unchangedCount;
    private int partialFailed;
    private String serviceName = "";

    private Attempt(HarvestRequest request, Long runId, LocalDateTime collectedAt) {
      this.request = request;
      this.projectId = request.job().getProjectId();
      this.runId = runId;
      this.collectedAt = collectedAt;
      this.dataSourceId = request.job().getDataSourceId();
      this.collectColumns = Boolean.TRUE.equals(request.job().getCollectColumns());
    }

    void execute() {
      if (!catalogSource.available()) {
        throw new MetadataException(
            MetadataErrorCode.PROVIDER_UNAVAILABLE, "数据源目录能力未装配，无法执行物理采集");
      }
      String databaseType = catalogSource.databaseType(dataSourceId);
      serviceName =
          orDefault(catalogSource.databaseServiceName(dataSourceId), "datasource-" + dataSourceId);
      Long serviceId = writeContainer(TYPE_SERVICE, "datasource:" + dataSourceId, serviceName, null,
          (row, type) -> {
            applyFqn(row, type, fqnContext(serviceName, "", "", "", ""));
            attributeCodec.applyTo(row, type, Map.of("dataSourceName", serviceName));
          });
      for (String database : databasesInScope()) {
        Long databaseId = writeContainer(TYPE_DATABASE, "database:" + dataSourceId + ":" + database,
            database, serviceId, (row, type) -> {
              row.setDatabaseName(truncate(database, IDENTIFIER_MAX));
              applyFqn(row, type, fqnContext(serviceName, database, "", "", ""));
              attributeCodec.applyTo(row, type, Map.of("databaseName", database));
            });
        for (String schema : schemasInScope(database)) {
          collectTablesOfScope(database, schema, databaseId, databaseType);
        }
      }
    }

    /** 容器层（数据源服务 / 库）各只有一行，父 id 一律回读一次：第二轮起这行是 UNCHANGED、不在变更集里。 */
    private Long writeContainer(
        String typeName, String assetKey, String name, Long parentId, RowCustomizer customizer) {
      TypeDefinition type = typeRegistry.require(typeName);
      CatalogAssetRow row = newRow(type, assetKey, name);
      row.setParentAssetId(parentId);
      customizer.accept(row, type);
      write(type, List.of(row));
      return request.dryRun() ? null : parentIdOf(assetKey);
    }

    private HarvestSummary summary(Long runId, RuntimeException failure) {
      RunStatus status = failure != null ? RunStatus.FAILED : RunStatus.SUCCESS;
      if (failure == null && presence != null && presence.suspect()) {
        status = RunStatus.SUSPECT;
      }
      return new HarvestSummary(
          runId,
          status,
          seen,
          newCount,
          changedCount,
          unchangedCount,
          partialFailed,
          presence == null ? 0 : presence.goneCount(),
          List.copyOf(scopes),
          failure == null ? (presence == null ? null : presence.reason())
              : truncate(message(failure), ERROR_MAX));
    }

    /**
     * 交给在场性判定的一轮事实。
     *
     * <p>上一轮的边界时刻在这里读、不在 {@link MetadataPresenceService} 里读：它要排除<b>本轮</b>自己，
     * 而"本轮是哪一行"只有采集现场知道。
     */
    private PresenceFacts presenceFacts() {
      MdCollectJobPO job = request.job();
      return new PresenceFacts(
          projectId,
          String.valueOf(dataSourceId),
          runId,
          request.operator(),
          request.dryRun(),
          job.getDatabaseName(),
          job.getSchemaName(),
          job.getTablePattern(),
          collectColumns,
          Set.copyOf(seenKeys),
          List.copyOf(scopes),
          presenceService.previousRoundStartedAt(projectId, job.getId(), runId),
          collectedAt);
    }

    private List<String> databasesInScope() {
      String configured = orNull(request.job().getDatabaseName());
      if (configured != null) {
        // 用户点名了库就不再枚举：既省一次目录调用，也让"明知是系统库仍要采"这种决定归配置方，
        // 而不是被下面那条排除表悄悄改掉。
        return List.of(normalizeIdentifier(configured));
      }
      List<String> databases = new ArrayList<>();
      for (String database : catalogSource.listDatabases(dataSourceId)) {
        String normalized = normalizeIdentifier(database);
        if (!normalized.isEmpty() && !HarvestSystemDatabases.excluded(normalized)) {
          databases.add(normalized);
        }
      }
      return databases;
    }

    private List<String> schemasInScope(String database) {
      String configured = orNull(request.job().getSchemaName());
      if (configured != null) {
        return List.of(normalizeIdentifier(configured));
      }
      List<String> schemas = new ArrayList<>();
      for (String schema : catalogSource.listSchemas(dataSourceId, database)) {
        String normalized = normalizeIdentifier(schema);
        if (!normalized.isEmpty()) {
          schemas.add(normalized);
        }
      }
      // MySQL/Doris 一侧 schema 恒空（Doris 的 listSchemas 直接返回空集合）。空集不是"没有表"，
      // 而是"这一级不存在"——用空串当哨兵继续往下走，键的拼法才与 TableIdentityResolver 逐字一致。
      return schemas.isEmpty() ? List.of(NO_SCHEMA) : schemas;
    }

    private void collectTablesOfScope(
        String database, String schema, Long databaseId, String databaseType) {
      List<DataSourceTable> tables;
      try {
        tables = catalogSource.listTables(dataSourceId, database, schema);
      } catch (RuntimeException failure) {
        // 连表清单都没读到 → 整个作用域本轮不可判 GONE（ticket 115 靠 failed=true 跳过）。
        log.warn(
            "表清单读取失败，本轮跳过该作用域的在场性判断 dataSource={} scope={}.{}",
            dataSourceId,
            database,
            schema,
            failure);
        // 失败的 scope 不能作为下一轮的“完整上一轮”证据；
        // 写入 run 的 partial 计数，以便 Presence 的历史闸门识别。
        partialFailed++;
        scopes.add(new ScopeOutcome(database, schema, 0, 0, true));
        return;
      }
      List<DataSourceTable> matched = new ArrayList<>();
      for (DataSourceTable table : tables == null ? List.<DataSourceTable>of() : tables) {
        if (table != null && orNull(table.getName()) != null
            && matchesPattern(table.getName(), request.job().getTablePattern())) {
          matched.add(table);
        }
      }
      Map<String, TableStats> stats =
          statsRegistry.load(databaseType, dataSourceId, database, schema);
      int partialTables = 0;
      for (List<DataSourceTable> chunk : partition(matched, TABLE_CHUNK_SIZE)) {
        partialTables += collectChunk(chunk, database, schema, databaseId, stats);
      }
      partialFailed += partialTables;
      scopes.add(new ScopeOutcome(database, schema, matched.size(), partialTables, false));
    }

    /** @return 本组里列读取失败的表数 */
    private int collectChunk(
        List<DataSourceTable> chunk,
        String database,
        String schema,
        Long databaseId,
        Map<String, TableStats> stats) {
      TypeDefinition tableType = typeRegistry.require(TYPE_TABLE);
      List<TablePending> pending = new ArrayList<>();
      int partialTables = 0;
      for (DataSourceTable table : chunk) {
        String tableName = normalizeIdentifier(table.getName());
        String tableKey = tableAssetKey(database, schema, tableName);
        ColumnsRead columns = readColumns(table, database, schema);
        if (columns.unreadable()) {
          partialTables++;
        }
        pending.add(
            new TablePending(
                tableKey,
                tableName,
                tableRow(tableType, tableKey, database, schema, databaseId, table, columns, stats),
                columns));
      }
      Map<String, Long> tableIds = new LinkedHashMap<>(write(tableType,
          pending.stream().map(TablePending::row).toList()));
      if (!request.dryRun()) {
        // 上一行只交回"本轮变更过"的行；未变更的表同样要能挂新列，所以整组键回读一次 id。
        // 少这一步，这些表的列会永久挂在空父级上——而这是没有任何报错的静默扁平。
        tableIds.putAll(upsertRepository.idsOf(projectId,
            pending.stream().map(TablePending::assetKey).toList()));
      }
      if (collectColumns) {
        writeColumnsOfChunk(database, schema, tableIds, pending);
      }
      return partialTables;
    }

    private void writeColumnsOfChunk(
        String database, String schema, Map<String, Long> tableIds, List<TablePending> pending) {
      TypeDefinition columnType = typeRegistry.require(TYPE_COLUMN);
      List<CatalogAssetRow> rows = new ArrayList<>();
      for (TablePending table : pending) {
        if (table.columns().unreadable() || table.columns().notRequested()) {
          continue;
        }
        Long parentId = tableIds.get(table.assetKey());
        for (DataSourceColumn column : table.columns().values()) {
          rows.add(columnRow(columnType, table, column, parentId, database, schema));
        }
        if (rows.size() >= AssetUpsertRepository.WRITE_BATCH_SIZE) {
          write(columnType, rows);
          rows = new ArrayList<>();
        }
      }
      write(columnType, rows);
    }

    private CatalogAssetRow tableRow(
        TypeDefinition type,
        String assetKey,
        String database,
        String schema,
        Long databaseId,
        DataSourceTable table,
        ColumnsRead columns,
        Map<String, TableStats> stats) {
      String tableName = normalizeIdentifier(table.getName());
      CatalogAssetRow row = newRow(type, assetKey, table.getName());
      // parent_asset_id 是 lineage 拥有的列、本模块只在 INSERT 时写一次：第一轮没挂上就永久是平的。
      row.setParentAssetId(databaseId);
      row.setDatabaseName(truncate(database, IDENTIFIER_MAX));
      row.setSchemaName(truncate(emptyToNull(schema), IDENTIFIER_MAX));
      row.setTableName(truncate(tableName, IDENTIFIER_MAX));
      row.setSummary(truncate(table.getRemarks(), SUMMARY_MAX));
      // 列没读全时不写指纹：写"零列"的指纹等于把"读不到"说成"这张表没有列"。
      row.setContentHash(
          columns.readable()
              ? MetadataStructureFingerprint.contentHash(table, columns.values())
              : null);
      Map<String, Object> attributes = new LinkedHashMap<>();
      attributes.put("databaseName", database);
      attributes.put("schemaName", emptyToNull(schema));
      attributes.put("tableName", tableName);
      attributes.put("tableType", table.getType());
      attributes.put("tableComment", table.getRemarks());
      if (columns.readable()) {
        attributes.put("columnCount", columns.values().size());
      }
      TableStats statsRow = stats.get(tableName);
      if (statsRow != null) {
        attributes.put("rowCountApprox", statsRow.rowCountApprox());
        attributes.put("partitioned", statsRow.partitioned());
        attributes.put("lastDdlTime", statsRow.lastDdlTime());
      }
      applyFqn(row, type, fqnContext(serviceName, database, schema, tableName, ""));
      attributeCodec.applyTo(row, type, attributes);
      return row;
    }

    private CatalogAssetRow columnRow(
        TypeDefinition type,
        TablePending table,
        DataSourceColumn column,
        Long parentId,
        String database,
        String schema) {
      String columnName = normalizeIdentifier(column.getName());
      // 键必须与血缘侧逐字相同：columnOf 只小写不去空格，所以这里交规范化后的名字，
      // 否则同一个列在血缘图里成两个节点，且没有任何 DDL 会报错。
      CatalogAssetRow row =
          newRow(type, PhysicalTableAssetKey.columnOf(table.assetKey(), columnName), column.getName());
      row.setParentAssetId(parentId);
      row.setDatabaseName(truncate(database, IDENTIFIER_MAX));
      row.setSchemaName(truncate(emptyToNull(schema), IDENTIFIER_MAX));
      row.setTableName(truncate(table.tableName(), IDENTIFIER_MAX));
      row.setColumnName(truncate(columnName, IDENTIFIER_MAX));
      row.setSummary(truncate(column.getRemarks(), SUMMARY_MAX));
      row.setContentHash(
          MetadataKeyCodec.digestHex(MetadataStructureFingerprint.columnFingerprint(column)));
      Map<String, Object> attributes = new LinkedHashMap<>();
      attributes.put("columnName", columnName);
      attributes.put("dataType", column.getTypeName());
      attributes.put("columnSize", column.getSize());
      attributes.put("decimalDigits", column.getScale());
      attributes.put("nullable", column.isNullable());
      attributes.put("ordinalPosition", column.getOrdinalPosition());
      attributes.put("primaryKey", column.isPrimaryKey());
      attributes.put("columnComment", column.getRemarks());
      applyFqn(
          row, type, fqnContext(serviceName, database, schema, table.tableName(), columnName));
      attributeCodec.applyTo(row, type, attributes);
      return row;
    }

    /** 列读取结果三态：读到 / 读不到 / 本轮没要求。后两者都不产列行，但只有中间那个算 PARTIAL。 */
    private ColumnsRead readColumns(DataSourceTable table, String database, String schema) {
      if (!collectColumns) {
        return ColumnsRead.NOT_REQUESTED;
      }
      try {
        List<DataSourceColumn> columns =
            catalogSource.listColumns(dataSourceId, database, schema, table.getName());
        if (columns == null || columns.isEmpty()) {
          log.warn(
              "列清单为空，按读取失败处理（非 JDBC 插件常态）dataSource={} table={}.{}.{}",
              dataSourceId,
              database,
              schema,
              table.getName());
          return ColumnsRead.UNREADABLE;
        }
        return new ColumnsRead(true, false, columns);
      } catch (RuntimeException failure) {
        log.warn(
            "列清单读取失败 dataSource={} table={}.{}.{}",
            dataSourceId,
            database,
            schema,
            table.getName(),
            failure);
        return ColumnsRead.UNREADABLE;
      }
    }

    private CatalogAssetRow newRow(TypeDefinition type, String assetKey, String name) {
      CatalogAssetRow row = new CatalogAssetRow();
      row.setProjectId(projectId);
      row.setAssetKey(assetKey);
      row.setAssetType(type.type().getLineageAssetType());
      row.setTypeId(type.type().getId());
      row.setName(truncate(orDefault(name, assetKey), NAME_MAX));
      row.setDisplayName(truncate(orDefault(name, assetKey), DISPLAY_NAME_MAX));
      row.setSourceType(SOURCE_TYPE);
      row.setSourceId(String.valueOf(dataSourceId));
      row.setDataSourceId(String.valueOf(dataSourceId));
      row.setFqnHash(MetadataKeyCodec.fqnHash(assetKey));
      row.setProviderType(ProviderType.HARVESTED.name());
      row.setEntityStatus(MetadataEntityStatus.UNPROCESSED.value());
      row.setCollectJobId(request.job().getId());
      row.setFirstSeenAt(collectedAt);
      row.setLastCollectAt(collectedAt);
      row.setUpdatedBy(request.operator());
      return row;
    }

    private void applyFqn(CatalogAssetRow row, TypeDefinition type, Map<String, String> context) {
      row.setFullyQualifiedName(
          truncate(
              MetadataKeyCodec.renderFqn(
                  type.type().getFqnPattern(), type.type().getKeySeparator(), context),
              FQN_MAX));
    }

    private String tableAssetKey(String database, String schema, String tableName) {
      return PhysicalTableAssetKey.of(
          String.valueOf(dataSourceId), database, normalizeIdentifier(schema), tableName);
    }

    private Long parentIdOf(String assetKey) {
      Collection<Long> ids = upsertRepository.idsOf(projectId, List.of(assetKey)).values();
      return ids.isEmpty() ? null : ids.iterator().next();
    }

    private Map<String, Long> write(TypeDefinition type, List<CatalogAssetRow> rows) {
      if (rows.isEmpty()) {
        return Map.of();
      }
      HarvestBatchResult result =
          upsertRepository.write(
              new BatchCommand(
                  projectId,
                  type.typeName(),
                  ProviderType.HARVESTED,
                  runId,
                  request.operator(),
                  collectedAt,
                  request.dryRun(),
                  rows));
      seen += result.newCount() + result.changedCount() + result.unchangedCount();
      newCount += result.newCount();
      changedCount += result.changedCount();
      unchangedCount += result.unchangedCount();
      rows.forEach(row -> seenKeys.add(row.getAssetKey()));
      return result.assetIds();
    }
  }

  @FunctionalInterface
  private interface RowCustomizer {
    void accept(CatalogAssetRow row, TypeDefinition type);
  }

  /** 一张表的现场：行、规范表名、它的列。列行要父 id，所以要等表写完才拼。 */
  private record TablePending(
      String assetKey, String tableName, CatalogAssetRow row, ColumnsRead columns) {
  }

  /** 列读取的三态结果。 */
  private record ColumnsRead(boolean readable, boolean unreadable, List<DataSourceColumn> values) {

    static final ColumnsRead NOT_REQUESTED = new ColumnsRead(false, false, List.of());
    static final ColumnsRead UNREADABLE = new ColumnsRead(false, true, List.of());

    boolean notRequested() {
      return !readable && !unreadable;
    }
  }

  /** 一轮采集对外的结论。 */
  public record HarvestSummary(
      Long runId,
      RunStatus status,
      int total,
      int newCount,
      int changedCount,
      int unchangedCount,
      int partialFailed,
      int goneCount,
      List<ScopeOutcome> scopes,
      String errorMessage) {

    public boolean succeeded() {
      return status == RunStatus.SUCCESS;
    }
  }

  /**
   * 一个 (database, schema) 作用域的在场性结论，ticket 115 判 GONE 的唯一依据。
   *
   * <p>{@code failed=true} 时该作用域整体跳过；{@code partialTables>0} 时列级跳过、表级照判。
   * 缺了这两个标记，一轮目录读取失败就会被下一轮解释成"整个库的表都被删了"。
   */
  public record ScopeOutcome(
      String databaseName, String schemaName, int tables, int partialTables, boolean failed) {

    public boolean tablesReadable() {
      return !failed;
    }

    public boolean columnsComplete() {
      return !failed && partialTables == 0;
    }
  }

  /** 请求即任务：作用域、是否 dry-run、触发方式都在任务行上，不在参数里再造一份。 */
  public record HarvestRequest(
      MdCollectJobPO job, TriggerType triggerType, boolean dryRun, String operator) {}
}
