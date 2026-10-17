package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.boot.YakOpsApplication;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.lineage.dao.mapper.LineageWriteMapper;
import io.yak.ops.business.lineage.dao.model.LineageAssetPO;
import io.yak.ops.business.metadata.dao.mapper.LineageCatalogRowMapper;
import io.yak.ops.business.metadata.dao.model.CatalogAssetRow;
import io.yak.ops.business.metadata.query.MetadataSearchBackend;
import io.yak.ops.business.metadata.query.MetadataSearchService;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.business.workflow.repository.WorkflowExecutionOverviewRepositoryAdapter;
import io.yak.ops.business.quality.repository.QualityExecutionOverviewRepositoryAdapter;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionOverviewRepositoryAdapter;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobCommandMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceOverviewMapper;
import io.yak.ops.business.mdm.processing.MdmMasterSqlGenerator;
import io.yak.ops.business.mdm.processing.MdmMasterSqlGenerator.AttributeSpec;
import io.yak.ops.business.mdm.processing.MdmMasterSqlGenerator.LandingSpec;
import io.yak.ops.business.quality.dao.mapper.QualityWriteMapper;
import io.yak.ops.business.quality.dao.mapper.QualityMonitorMapper;
import io.yak.ops.business.quality.dao.mapper.QualityQueryMapper;
import io.yak.ops.business.quality.dao.model.QualityMonitorPO;
import io.yak.ops.business.semantic.dao.mapper.SemanticStandardMapper;
import io.yak.ops.business.quality.dao.model.QualityTableAssetPO;
import io.yak.ops.business.workflow.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.business.workflow.dao.model.WorkflowDefinitionPO;
import io.yak.ops.business.workflow.repository.WorkflowRuntimeSingleMasterGuard;
import java.time.Instant;
import java.time.LocalDateTime;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Requires an owned empty database, never an existing user's database. */
@EnabledIfEnvironmentVariable(named = "ARCHITECTURE_POSTGRESQL_URL", matches = ".+")
class PostgresqlStorageSmokeTest {
  /**
   * R12-2 forward V2 storage smoke on the disposable CI PostgreSQL database.
   * No binding API is enabled and no SQL Runtime execution is performed.
   */
  private static void verifyDwdBindingForwardMigration(
      JdbcTemplate jdbc, PlatformTransactionManager manager, long projectId) {
    String sql = "INSERT INTO yak_dev_model_task_binding "
        + "(project_id, model_id, model_version_id, model_version_no, "
        + "mapping_revision_id, mapping_sha256, logical_sha256, physical_sha256, "
        + "current_standard_sha256, node_id, task_revision_id, task_revision_no, "
        + "task_definition_sha256, target_datasource_id, target_database, "
        + "target_schema, target_table, write_mode, binding_digest, "
        + "idempotency_key, state, create_time) "
        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)";
    var tx = new TransactionTemplate(manager);
    String key = UUID.randomUUID().toString();
    tx.execute(status -> {
      insertDwdBindingRow(jdbc, sql, projectId, 7001L, 8001L, key);
      assertThat(jdbc.queryForObject(
          "SELECT COUNT(*) FROM yak_dev_model_task_binding "
              + "WHERE project_id=? AND idempotency_key=?", Long.class, projectId, key))
          .isEqualTo(1L);
      status.setRollbackOnly();
      return null;
    });
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM yak_dev_model_task_binding "
            + "WHERE project_id=? AND idempotency_key=?", Long.class, projectId, key))
        .isZero();

    insertDwdBindingRow(jdbc, sql, projectId, 7001L, 8001L, key);
    assertThatThrownBy(() ->
        insertDwdBindingRow(jdbc, sql, projectId, 7002L, 8002L, key))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
        insertDwdBindingRow(jdbc, sql, projectId, 7001L, 8001L,
            UUID.randomUUID().toString()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM yak_dev_model_task_binding "
            + "WHERE project_id=? AND node_id=? AND task_revision_id=?",
        Long.class, projectId, 7001L, 8001L)).isEqualTo(1L);
  }

  private static void insertDwdBindingRow(
      JdbcTemplate jdbc, String sql, long projectId, long nodeId, long revisionId, String key) {
    int count = jdbc.update(sql, projectId, 501L, 901L, 3, 9011L,
        "a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64),
        nodeId, revisionId, 2, "e".repeat(64), 9001L,
        "warehouse", "public", "dwd_order_detail", "INSERT",
        "f".repeat(64), key, "BINDING_RECORDED_NOT_EXECUTABLE");
    assertThat(count).isEqualTo(1);
  }

  @Test
  void freshDeploymentWritesAndDurableRestart() throws Exception {
    String password = UUID.randomUUID().toString();
    long projectId;
    try (var context = start(password)) {
      validate(context);
      JdbcTemplate jdbc = new JdbcTemplate(context.getBean("yakBusinessDataSource", DataSource.class));
      var security = new JdbcTemplate(context.getBean("yakSecurityDataSource", DataSource.class));
      projectId = security.queryForObject("SELECT id FROM yak_security_project ORDER BY id LIMIT 1", Long.class);
      assertThat(projectId).isPositive();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM yak_quality_rule_template", Long.class)).isPositive();
      assertThat(security.queryForObject("SELECT COUNT(*) FROM yak_security_user WHERE user_name = 'pg_storage_owner'", Long.class)).isEqualTo(1);
      verifyLoginAndProjectRequest(context, password, projectId);
      verifyAtomicBusinessWrites(context, jdbc, projectId);
      verifySharedCatalogAndSearch(context, jdbc, projectId);
      verifyRuntimeProjections(context, jdbc, projectId);
      verifyMasterRefresh(jdbc, projectId);
      verifyOdsExactSourceUniqueness(jdbc, projectId);
      DwdMappingSqlSmoke.verify(
          context.getBean("yakBusinessDataSource", DataSource.class),
          context.getBean("yakBusinessTransactionManager", PlatformTransactionManager.class));
      verifyDwdBindingForwardMigration(
          jdbc, context.getBean("yakBusinessTransactionManager", PlatformTransactionManager.class),
          projectId);
      var state = AgentState.builder().userId("pg-storage-user").sessionId("pg-storage-session").build();
      state.setSummary("durable agent state");
      var stateStore = context.getBean(AgentStateStore.class);
      stateStore.save("pg-storage-user", "pg-storage-session", "agent_state", state);
      assertThat(stateStore.get("other-user", "pg-storage-session", "agent_state", AgentState.class)).isEmpty();
      var contender = new WorkflowRuntimeSingleMasterGuard(context.getBean("yakBusinessDataSource", DataSource.class), true);
      assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(contender, "acquire"))
          .isInstanceOf(IllegalStateException.class).hasMessageContaining("单 Master");
      context.getBean(Scheduler.class).addJob(JobBuilder.newJob(NoopJob.class)
          .withIdentity("pg-storage-restart").storeDurably().build(), false);
    }
    try (var context = start(password)) {
      validate(context);
      var jdbc = new JdbcTemplate(context.getBean("yakBusinessDataSource", DataSource.class));
      var security = new JdbcTemplate(context.getBean("yakSecurityDataSource", DataSource.class));
      assertThat(security.queryForObject("SELECT COUNT(*) FROM yak_security_user WHERE user_name = 'pg_storage_owner'", Long.class)).isEqualTo(1);
      assertThat(security.queryForObject("SELECT id FROM yak_security_project ORDER BY id LIMIT 1", Long.class)).isEqualTo(projectId);
      assertThat(context.getBean(Scheduler.class).checkExists(new JobKey("pg-storage-restart"))).isTrue();
      assertThat(jdbc.queryForObject("SELECT version FROM yak_mdm_record WHERE entity_id = 987654321", Long.class)).isEqualTo(2);
      var stateStore = context.getBean(AgentStateStore.class);
      assertThat(stateStore.get("pg-storage-user", "pg-storage-session", "agent_state", AgentState.class)
          .orElseThrow().getSummary()).isEqualTo("durable agent state");
      stateStore.delete("pg-storage-user", "pg-storage-session");
      assertThat(stateStore.exists("pg-storage-user", "pg-storage-session")).isFalse();
    }
  }

  private void verifyRuntimeProjections(ConfigurableApplicationContext context, JdbcTemplate jdbc, long projectId) {
    LocalDateTime start = LocalDateTime.of(2026, 1, 2, 0, 0);
    LocalDateTime end = start.plusDays(1);
    jdbc.update("""
        INSERT INTO yak_workflow_execution
          (id, project_id, definition_id, status, input_json, created_at, updated_at, run_started_at, ended_at)
        VALUES ('pg-runtime-projection', ?, 'pg-storage-workflow', 'SUCCESS', '{}', ?, ?, ?, ?)
        """, projectId, start, start.plusSeconds(2), start, start.plusSeconds(2));
    DataSource database = context.getBean("yakBusinessDataSource", DataSource.class);
    CurrentProject scoped = () -> Optional.of(new ProjectContext(projectId, "storage-test"));
    var workflow = new WorkflowExecutionOverviewRepositoryAdapter(database, scoped);
    assertThat(workflow.overview(start, end, true).latest().durationMs()).isEqualTo(2000);
    assertThat(workflow.taskSummary("pg-storage-workflow", start, end).lastDurationMs()).isEqualTo(2000);
    assertThat(workflow.metrics(start, end).durationTotalMs()).isEqualTo(2000);
    assertThat(workflow.overview(start, end, true).trend()).hasSize(1);
    new QualityExecutionOverviewRepositoryAdapter(database, scoped).overview(start, end, true);
    new OfflineExecutionOverviewRepositoryAdapter(database, scoped).overview(start, end, true);
    jdbc.update("""
        INSERT INTO yak_ops_data_service_call_log
          (project_id, api_id, service_name, service_path, success, create_time)
        VALUES (?, 987654321, 'PG test service', '/pg-storage-test', 1, ?)
        """, projectId, start.plusMinutes(17));
    var service = context.getBean(DataServiceOverviewMapper.class);
    assertThat(service.selectTrend(projectId, start, end, 15)).hasSize(1);
    assertThat(service.selectTrend(projectId, start, end, 15).getFirst().getBucketIndex()).isEqualTo(1);
    var realtime = context.getBean(RealtimeJobCommandMapper.class);
    jdbc.update("UPDATE yak_realtime_runtime_lease SET lease_until = '1970-01-01', lease_owner = NULL WHERE id = 1");
    assertThat(realtime.tryAcquireLease("pg-storage-owner", 60)).isEqualTo(1);
    assertThat(realtime.tryAcquireLease("pg-storage-contender", 60)).isZero();
    assertThat(realtime.tryAcquireLease("pg-storage-owner", 60)).isEqualTo(1);
    jdbc.update("UPDATE yak_realtime_runtime_lease SET lease_until = '1970-01-01' WHERE id = 1");
  }

  private void verifyLoginAndProjectRequest(ConfigurableApplicationContext context, String password, long projectId) throws Exception {
    int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
    String base = "http://127.0.0.1:" + port;
    ObjectMapper json = context.getBean(ObjectMapper.class);
    var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    var response = client.send(HttpRequest.newBuilder(URI.create(base + "/yak-security/api/v1/account/login"))
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
            json.writeValueAsString(Map.of("userName", "pg_storage_owner", "pw", password)))).build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(json.readTree(response.body()).path("code").asInt()).isEqualTo(200);
    var types = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/metadata/types"))
        .header("X-YAK-SECURITY-PROJECT-ID", Long.toString(projectId)).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertThat(json.readTree(types.body()).path("code").asInt()).isEqualTo(200);
    assertThat(json.readTree(types.body()).path("data").size()).isPositive();
  }

  private void verifyAtomicBusinessWrites(ConfigurableApplicationContext context, JdbcTemplate jdbc, long projectId) {
    var workflow = new WorkflowDefinitionPO();
    workflow.setId("pg-storage-workflow");
    workflow.setProjectId(projectId);
    workflow.setName("PostgreSQL workflow");
    workflow.setStatus("OFFLINE");
    workflow.setDraftRevision(1L);
    workflow.setLatestVersionNo(0);
    workflow.setDraftJson("{\"nodes\":[]}");
    workflow.setCreateTime(Instant.now());
    workflow.setUpdateTime(Instant.now());
    var definitions = context.getBean(WorkflowDefinitionMapper.class);
    assertThat(definitions.upsert(workflow)).isEqualTo(1);
    workflow.setName("Updated workflow");
    assertThat(definitions.upsert(workflow)).isEqualTo(1);
    assertThat(definitions.selectById(workflow.getId()).getName()).isEqualTo("Updated workflow");

    QualityTableAssetPO asset = new QualityTableAssetPO();
    asset.setProjectId(projectId);
    asset.setDataSourceId(987654321L);
    asset.setDataSourceName("PostgreSQL");
    asset.setDatabaseName("storage_test");
    asset.setSchemaName("public");
    asset.setTableName("test_table");
    asset.setTableType("TABLE");
    asset.setRegisteredBy("pg_storage_owner");
    var writer = context.getBean(QualityWriteMapper.class);
    writer.upsertTableAsset(asset);
    writer.upsertTableAsset(asset);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM yak_quality_table_asset WHERE data_source_id = 987654321", Long.class)).isEqualTo(1);
    var transactions = new TransactionTemplate(context.getBean("yakBusinessTransactionManager", PlatformTransactionManager.class));
    transactions.execute(status -> {
      jdbc.update("UPDATE yak_quality_table_asset SET deleted = 1 WHERE data_source_id = 987654321");
      status.setRollbackOnly();
      return null;
    });
    assertThat(jdbc.queryForObject("SELECT deleted FROM yak_quality_table_asset WHERE data_source_id = 987654321", Integer.class)).isZero();
    var monitor = new QualityMonitorPO();
    monitor.setProjectId(projectId);
    monitor.setMonitorName("PG numeric boolean");
    monitor.setDataSourceId(987654321L);
    monitor.setDataSourceName("PostgreSQL");
    monitor.setTableName("test_table");
    monitor.setOwner("pg_storage_owner");
    monitor.setEnabled(true);
    monitor.setDeleted(false);
    var monitors = context.getBean(QualityMonitorMapper.class);
    monitors.insert(monitor);
    assertThat(monitors.selectById(monitor.getId()).getEnabled()).isTrue();
    assertThat(context.getBean(QualityQueryMapper.class).selectTableSummaries(
        Map.of("projectId", projectId, "dataSourceId", 987654321L))).hasSize(1);
    var standards = context.getBean(SemanticStandardMapper.class);
    standards.selectListRows(new Page<>(1, 20), projectId, "CODE", "test", "ENABLED");
    assertThat(standards.selectEnabledCodeSetOptions(projectId)).isEmpty();
  }

  private void verifySharedCatalogAndSearch(ConfigurableApplicationContext context, JdbcTemplate jdbc, long projectId) {
    var lineage = new LineageAssetPO();
    lineage.setProjectId(projectId);
    lineage.setAssetKey("pg-storage:catalog");
    lineage.setAssetType("TABLE");
    lineage.setName("lineage-owned-name");
    lineage.setSourceType("DATASOURCE");
    lineage.setSourceId("123");
    lineage.setProperties("{\"lineage\":true}");
    context.getBean(LineageWriteMapper.class).upsertAsset(lineage);
    var row = new CatalogAssetRow();
    row.setProjectId(projectId);
    row.setAssetKey(lineage.getAssetKey());
    row.setAssetType("TABLE");
    row.setName("catalog-must-not-overwrite");
    row.setSourceType("DATASOURCE");
    row.setSourceId("123");
    row.setTypeId(jdbc.queryForObject("SELECT id FROM yak_md_type_def WHERE type_name = 'table'", Long.class));
    row.setDisplayName("订单明细 100%_literal");
    row.setEntityStatus("ACTIVE");
    row.setProviderType("HARVESTED");
    row.setContentHash("content-one");
    row.setSourceHash("source-one");
    row.setUpdatedBy("pg_storage_owner");
    row.setMdAttributes("{\"s_bool_1\":true,\"s_date_1\":\"2026-01-02 03:04:05.123456\"}");
    context.getBean(LineageCatalogRowMapper.class).upsertAssets(List.of(row));
    LocalDateTime updated = jdbc.queryForObject("SELECT update_time FROM yak_metadata_asset WHERE asset_key = ?", LocalDateTime.class, row.getAssetKey());
    context.getBean(LineageCatalogRowMapper.class).upsertAssets(List.of(row));
    assertThat(jdbc.queryForObject("SELECT update_time FROM yak_metadata_asset WHERE asset_key = ?", LocalDateTime.class, row.getAssetKey())).isEqualTo(updated);
    assertThat(jdbc.queryForObject("SELECT name FROM yak_metadata_asset WHERE asset_key = ?", String.class, row.getAssetKey())).isEqualTo("lineage-owned-name");
    context.getBean(LineageWriteMapper.class).upsertAsset(lineage);
    assertThat(jdbc.queryForObject("SELECT display_name FROM yak_metadata_asset WHERE asset_key = ?", String.class, row.getAssetKey())).isEqualTo(row.getDisplayName());
    assertThat(jdbc.queryForObject("SELECT s_bool_1 FROM yak_metadata_asset WHERE asset_key = ?", Integer.class, row.getAssetKey())).isEqualTo(1);
    var backend = context.getBean(MetadataSearchBackend.class);
    var search = new MetadataSearchService(context.getBean(MetadataTypeRegistry.class),
        () -> Optional.of(new ProjectContext(projectId, "storage-test")), backend, context.getBean(ObjectMapper.class));
    var otherProject = new MetadataSearchService(context.getBean(MetadataTypeRegistry.class),
        () -> Optional.of(new ProjectContext(projectId + 1000, "other-project")), backend, context.getBean(ObjectMapper.class));
    for (String term : List.of("订单", "missing 订单", "100%_literal")) {
      var query = new MetadataSearchService.SearchRequest(term, List.of("table"), Map.of(), Map.of(),
          List.of(), List.of(), null, null, null, 0, 20, false, true, true);
      var result = search.search(query);
      assertThat(result.items()).hasSize(1);
      assertThat(result.typeFacets()).anySatisfy(facet -> {
        assertThat(facet.typeName()).isEqualTo("table");
        assertThat(facet.count()).isEqualTo(1);
      });
      assertThat(otherProject.search(query).items()).isEmpty();
    }
  }


  private void verifyOdsExactSourceUniqueness(JdbcTemplate jdbc, long projectId) {
    // PostgreSQL schema-aware partial unique index; quoted schemas are case-sensitive.
    String prefix = "r10-pg-ods-" + UUID.randomUUID().toString().substring(0, 8);
    String insert = """
        INSERT INTO yak_modeling_model
          (project_id, model_code, model_name, dialect, layer_code,
           source_datasource_id, source_database, source_schema, source_table,
           deleted, status)
        VALUES (?, ?, 'R10 PostgreSQL ODS index smoke', 'POSTGRESQL', 'ODS',
                17, 'trade_db', ?, 'trade_order_detail', 0, 'DRAFT')
        """;
    try {
      jdbc.update(insert, projectId, prefix + "-public", "public");
      jdbc.update(insert, projectId, prefix + "-archive", "archive");
      jdbc.update(insert, projectId, prefix + "-default", null);
      assertThatThrownBy(() -> jdbc.update(insert, projectId, prefix + "-duplicate", "public"))
          .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
      assertThatThrownBy(() -> jdbc.update(insert, projectId, prefix + "-default-dup", null))
          .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
      jdbc.update("UPDATE yak_modeling_model SET deleted = 1 WHERE project_id = ? AND model_code = ?",
          projectId, prefix + "-public");
      jdbc.update(insert, projectId, prefix + "-rebound", "public");
      assertThat(jdbc.queryForObject("""
          SELECT COUNT(*) FROM yak_modeling_model
          WHERE project_id = ? AND deleted = 0
            AND source_datasource_id = 17 AND source_database = 'trade_db'
            AND source_schema = 'public' AND source_table = 'trade_order_detail'
          """, Long.class, projectId)).isEqualTo(1L);
    } finally {
      jdbc.update("DELETE FROM yak_modeling_model WHERE project_id = ? AND model_code LIKE ?",
          projectId, prefix + "%");
    }
  }

  private void verifyMasterRefresh(JdbcTemplate jdbc, long projectId) {
    jdbc.execute("CREATE TABLE mdm_landing_pg_test (source_id bigint, display_name text)");
    jdbc.update("INSERT INTO mdm_landing_pg_test VALUES (1, 'first')");
    String sql = MdmMasterSqlGenerator.generate(projectId, 987654321L, "pg_test",
        List.of(new AttributeSpec("source_id", true), new AttributeSpec("display_name", false)),
        new LandingSpec("public", "mdm_landing_pg_test", 123L), Map.of(), true);
    jdbc.execute(sql);
    jdbc.execute(sql);
    assertThat(jdbc.queryForObject("SELECT version FROM yak_mdm_record WHERE entity_id = 987654321", Long.class)).isEqualTo(1);
    jdbc.update("UPDATE yak_mdm_record SET attribute_overrides = '{\"display_name\":\"approved\"}'::jsonb WHERE entity_id = 987654321");
    jdbc.update("UPDATE mdm_landing_pg_test SET display_name = 'refreshed'");
    jdbc.execute(sql);
    jdbc.execute(sql);
    assertThat(jdbc.queryForObject("SELECT attributes ->> 'display_name' FROM yak_mdm_record WHERE entity_id = 987654321", String.class)).isEqualTo("approved");
    assertThat(jdbc.queryForObject("SELECT version FROM yak_mdm_record WHERE entity_id = 987654321", Long.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM yak_mdm_record WHERE project_id <> ? AND entity_id = 987654321", Long.class, projectId)).isZero();
  }

  private void validate(ConfigurableApplicationContext context) {
    assertThat(context.getBean("opsDataSource")).isSameAs(context.getBean("yakBusinessDataSource"));
    assertThat(context.getBeansOfType(Flyway.class)).hasSize(30);
    context.getBeansOfType(Flyway.class).values().forEach(Flyway::validate);
  }

  private ConfigurableApplicationContext start(String bootstrapPassword) {
    Map<String, Object> properties = Map.ofEntries(
        Map.entry("server.port", "0"),
        Map.entry("yak.database.url", System.getenv("ARCHITECTURE_POSTGRESQL_URL")),
        Map.entry("yak.database.username", System.getenv().getOrDefault("ARCHITECTURE_POSTGRESQL_USERNAME", "data_ops")),
        Map.entry("yak.database.password", System.getenv().getOrDefault("ARCHITECTURE_POSTGRESQL_PASSWORD", "")),
        Map.entry("yak.security.datasource.url", System.getenv().getOrDefault("ARCHITECTURE_POSTGRESQL_SECURITY_URL", System.getenv("ARCHITECTURE_POSTGRESQL_URL"))),
        Map.entry("yak.security.datasource.username", System.getenv().getOrDefault("ARCHITECTURE_POSTGRESQL_SECURITY_USERNAME", System.getenv().getOrDefault("ARCHITECTURE_POSTGRESQL_USERNAME", "data_ops"))),
        Map.entry("yak.security.datasource.password", System.getenv().getOrDefault("ARCHITECTURE_POSTGRESQL_SECURITY_PASSWORD", System.getenv().getOrDefault("ARCHITECTURE_POSTGRESQL_PASSWORD", ""))),
        Map.entry("yak.agent.enabled", "true"),
        Map.entry("yak.security.bootstrap.enabled", "true"),
        Map.entry("yak.security.bootstrap.username", "pg_storage_owner"),
        Map.entry("yak.security.bootstrap.password", bootstrapPassword),
        Map.entry("yak.project-space.compatibility.default-owner-username", "pg_storage_owner"),
        Map.entry("spring.quartz.auto-startup", "false"));
    return new SpringApplicationBuilder(YakOpsApplication.class).profiles("postgresql")
        .initializers(application -> application.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("isolated-postgresql-smoke", properties))).run();
  }

  public static class NoopJob implements Job {
    @Override
    public void execute(JobExecutionContext context) {}
  }
}
