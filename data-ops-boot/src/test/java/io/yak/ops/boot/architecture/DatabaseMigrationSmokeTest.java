package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.boot.YakOpsApplication;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;

/** CI owns an isolated MySQL schema; never run this against a user's existing database. */
@EnabledIfEnvironmentVariable(named = "ARCHITECTURE_MYSQL_URL", matches = ".+")
class DatabaseMigrationSmokeTest {
  @Test
  void emptyDatabaseThenCurrentBaselineRestart() {
    startAndValidate(true);
    startAndValidate(true);
  }

  private void startAndValidate(boolean datasourceEnabled) {
    startAndValidate(datasourceEnabled, context -> {});
  }

  @Test
  void concurrentAssetEditorsCannotOverwriteTheFirstCommittedDescription() {
    startAndValidate(true, context -> {
      var mapper = context.getBean(io.yak.ops.business.asset.dao.mapper.AssetItemMapper.class);
      var project = org.mockito.Mockito.mock(io.yak.ops.core.project.CurrentProject.class);
      org.mockito.Mockito.when(project.requireProjectId()).thenReturn(1L);
      var audit = org.mockito.Mockito.mock(io.yak.ops.business.audit.BusinessAuditService.class);
      org.mockito.Mockito.when(audit.start(org.mockito.ArgumentMatchers.any()))
          .thenReturn(org.mockito.Mockito.mock(io.yak.ops.business.audit.AuditOperationHandle.class));
      var service = new io.yak.ops.business.asset.application.AssetAppService(project, mapper, audit);
      var tx = new org.springframework.transaction.support.TransactionTemplate(
          context.getBean("yakBusinessTransactionManager", org.springframework.transaction.PlatformTransactionManager.class));
      var request = new io.yak.ops.business.asset.controller.v1.dto.AssetRequests.ManualRegisterDTO();
      request.setAssetCode("ai-race-" + java.util.UUID.randomUUID());
      request.setName("AI concurrency fixture");
      request.setDescription("initial");
      var created = tx.execute(status -> service.registerManual(request, "ci"));
      String definition = service.editableSnapshot(created.id()).definition();
      var firstWritten = new java.util.concurrent.CountDownLatch(1);
      var commitFirst = new java.util.concurrent.CountDownLatch(1);
      try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
        var first = workers.submit(() -> tx.execute(status -> {
          service.updateSnapshot(created.id(), null, "first committed", null, "ci", definition);
          firstWritten.countDown();
          await(commitFirst);
          return true;
        }));
        await(firstWritten);
        var second = workers.submit(() -> {
          try {
            tx.execute(status -> service.updateSnapshot(created.id(), null, "stale AI draft", null, "ci", definition));
            return false;
          } catch (io.yak.ops.business.asset.exception.AssetException conflict) { return true; }
        });
        commitFirst.countDown();
        assertThat(first.get(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(second.get(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(service.editableSnapshot(created.id()).description()).isEqualTo("first committed");
      } catch (Exception failure) {
        throw new AssertionError("Concurrent conditional updates failed", failure);
      } finally {
        commitFirst.countDown();
        mapper.deleteById(created.id());
      }
    });
  }

  @Test
  void dwdTaskBindingV2PreservesProjectScopedUniquenessAndRollbackOnMySql() {
    startAndValidate(true, context -> DwdTaskBindingSqlSmoke.verifyDwdBindingForwardMigration(
        new org.springframework.jdbc.core.JdbcTemplate(
            context.getBean("yakBusinessDataSource", DataSource.class)),
        context.getBean("yakBusinessTransactionManager",
            org.springframework.transaction.PlatformTransactionManager.class),
        990001L));
  }

  @Test
  void dwdMappingV6PersistsScopedRevisionsAndRollsBackAtomically() {
    startAndValidate(true, context -> DwdMappingSqlSmoke.verify(
        context.getBean("yakBusinessDataSource", DataSource.class),
        context.getBean("yakBusinessTransactionManager",
            org.springframework.transaction.PlatformTransactionManager.class)));
  }

  @Test
  void odsSourceUniqueIndexSeparatesSchemasAndBlocksDuplicateBindings() {
    // Uses the same isolated MySQL 8.0 CI database as the migration smoke.
    startAndValidate(true, context -> {
      var jdbc = new org.springframework.jdbc.core.JdbcTemplate(
          context.getBean("yakBusinessDataSource", DataSource.class));
      long projectId = java.util.UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
      long otherProjectId = java.util.UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
      String prefix = "r10-ods-" + java.util.UUID.randomUUID().toString().substring(0, 8);
      String insert = """
          INSERT INTO yak_modeling_model
            (project_id, model_code, model_name, dialect, layer_code,
             source_datasource_id, source_database, source_schema, source_table,
             deleted, status)
          VALUES (?, ?, 'R10 ODS index CI', 'MYSQL', 'ODS', 17,
                  'trade_db', ?, 'trade_order_detail', 0, 'DRAFT')
          """;
      try {
        jdbc.update(insert, projectId, prefix + "-public", "public");
        // Different schema is a different physical source even for identical names.
        jdbc.update(insert, projectId, prefix + "-archive", "archive");
        jdbc.update(insert, projectId, prefix + "-default", null);
        // Different projects may independently build ODS for the same datasource.
        jdbc.update(insert, otherProjectId, prefix + "-other", "public");
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> jdbc.update(insert, projectId, prefix + "-duplicate", "public"))
            .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> jdbc.update(insert, projectId, prefix + "-duplicate-null", null))
            .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        // Recycled models do not block a new active ODS binding.
        jdbc.update("UPDATE yak_modeling_model SET deleted = 1 WHERE project_id = ? AND model_code = ?",
            projectId, prefix + "-public");
        jdbc.update(insert, projectId, prefix + "-recreated", "public");
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM yak_modeling_model
            WHERE project_id = ? AND deleted = 0
              AND source_datasource_id = 17 AND source_database = 'trade_db'
              AND source_schema = 'public' AND source_table = 'trade_order_detail'
            """, Long.class, projectId)).isEqualTo(1L);
      } finally {
        jdbc.update("DELETE FROM yak_modeling_model WHERE project_id IN (?, ?)",
            projectId, otherProjectId);
      }
    });
  }

  private static void await(java.util.concurrent.CountDownLatch latch) {
    try {
      if (!latch.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Fixture barrier timed out");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }

  private void startAndValidate(boolean datasourceEnabled,
      java.util.function.Consumer<org.springframework.context.ConfigurableApplicationContext> acceptance) {
    Map<String, Object> properties = Map.ofEntries(
        Map.entry("server.port", "0"),
        Map.entry("yak.database.enabled", "true"),
        Map.entry("yak.database.url", System.getenv("ARCHITECTURE_MYSQL_URL")),
        Map.entry("yak.database.username", System.getenv().getOrDefault("ARCHITECTURE_MYSQL_USERNAME", "root")),
        Map.entry("yak.database.password", System.getenv().getOrDefault("ARCHITECTURE_MYSQL_PASSWORD", "")),
        Map.entry("yak.datasource.enabled", String.valueOf(datasourceEnabled)),
        Map.entry("yak.security.database-enabled", "true"),
        Map.entry("yak.security.datasource.url", System.getenv("ARCHITECTURE_MYSQL_URL")),
        Map.entry("yak.security.datasource.username", System.getenv().getOrDefault("ARCHITECTURE_MYSQL_USERNAME", "root")),
        Map.entry("yak.security.datasource.password", System.getenv().getOrDefault("ARCHITECTURE_MYSQL_PASSWORD", "")),
        Map.entry("yak.security.permission-registration.enabled", "true"),
        Map.entry("yak.security.bootstrap.enabled", "true"),
        Map.entry("yak.security.bootstrap.username", "architecture_ci_owner"),
        Map.entry("yak.security.bootstrap.password", java.util.UUID.randomUUID().toString()),
        Map.entry("yak.security.bootstrap.real-name", "Architecture CI"),
        Map.entry("yak.project-space.compatibility.default-owner-username", "architecture_ci_owner"),
        Map.entry("spring.quartz.auto-startup", "false"));
    try (var context = new SpringApplicationBuilder(YakOpsApplication.class)
        .initializers(application -> application.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("isolated-migration-smoke", properties))).run()) {
      assertThat(context.getBean("yakBusinessDataSource", DataSource.class)).isNotNull();
      assertThat(context.getBean("opsDataSource")).isSameAs(context.getBean("yakBusinessDataSource"));
      Map<String, Flyway> migrations = context.getBeansOfType(Flyway.class);
      assertThat(migrations).isNotEmpty();
      migrations.values().forEach(Flyway::validate);
      acceptance.accept(context);
    }
  }
}
