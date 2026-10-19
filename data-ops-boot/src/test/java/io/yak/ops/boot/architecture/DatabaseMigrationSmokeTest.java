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

  /**
   * B2 R1 T2-A admission prerequisite. Verify the *actual* wired Modeling and
   * Semantic MyBatis mappers participate in the same business data source and
   * transaction, rather than trusting a matching @Transactional bean name.
   *
   * <p>This is necessary but NOT sufficient for publication fencing: the new
   * Semantic revision write-path lock ordering and concurrent publish tests are
   * separate blocking evidence before production integration may start.
   */
  @Test
  void b2ModelingAndSemanticMappersShareRealBusinessTransactionOnMySql() {
    startAndValidate(true, context -> {
      var business = context.getBean("yakBusinessDataSource", DataSource.class);
      var manager = context.getBean("yakBusinessTransactionManager",
          org.springframework.transaction.PlatformTransactionManager.class);
      assertThat(manager)
          .isInstanceOf(org.springframework.jdbc.datasource.DataSourceTransactionManager.class);
      var jdbcManager = (org.springframework.jdbc.datasource.DataSourceTransactionManager) manager;
      assertThat(jdbcManager.getDataSource()).isSameAs(business);

      var factories = context.getBeansOfType(org.apache.ibatis.session.SqlSessionFactory.class);
      var modelingMapper = io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper.class;
      var semanticMapper = io.yak.ops.business.semantic.dao.mapper.SemanticStandardMapper.class;
      var modelingFactories = factories.values().stream()
          .filter(factory -> factory.getConfiguration().hasMapper(modelingMapper)).toList();
      var semanticFactories = factories.values().stream()
          .filter(factory -> factory.getConfiguration().hasMapper(semanticMapper)).toList();
      assertThat(modelingFactories).hasSize(1);
      assertThat(semanticFactories).hasSize(1);
      var modelDataSource = modelingFactories.get(0).getConfiguration()
          .getEnvironment().getDataSource();
      var semanticDataSource = semanticFactories.get(0).getConfiguration()
          .getEnvironment().getDataSource();
      assertThat(modelDataSource).isSameAs(semanticDataSource).isSameAs(business);

      var tx = new org.springframework.transaction.support.TransactionTemplate(manager);
      tx.execute(status -> {
        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
            .isActualTransactionActive()).isTrue();
        var bound = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(business);
        try {
          assertThat(org.springframework.jdbc.datasource.DataSourceUtils
              .getConnection(modelDataSource)).isSameAs(bound);
          assertThat(org.springframework.jdbc.datasource.DataSourceUtils
              .getConnection(semanticDataSource)).isSameAs(bound);
        } finally {
          org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(bound, business);
        }
        return null;
      });
    });
  }

  @Test
  void b2SemanticPublicationRevisionMigrationAndRollbackOnIsolatedMySql() {
    startAndValidate(true, context -> {
      var business = context.getBean("yakBusinessDataSource", DataSource.class);
      var jdbc = new org.springframework.jdbc.core.JdbcTemplate(business);
      var manager = context.getBean("yakBusinessTransactionManager",
          org.springframework.transaction.PlatformTransactionManager.class);
      var tx = new org.springframework.transaction.support.TransactionTemplate(manager);
      long project = (java.util.UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE) + 1;
      var trusted = org.mockito.Mockito.mock(io.yak.ops.core.project.CurrentProject.class);
      org.mockito.Mockito.when(trusted.requireProjectId()).thenReturn(project);
      var writer = new io.yak.ops.business.semantic.repository.SemanticPublicationRevisionService(
          business, trusted);
      String select = "SELECT revision_no FROM yak_semantic_publication_revision "
          + "WHERE project_id = ?";
      try {
        // Fail closed outside the real business transaction.
        org.assertj.core.api.Assertions.assertThatThrownBy(writer::advanceBeforeMutation)
            .isInstanceOf(IllegalStateException.class);
        // First edit in a new project rolls back its row creation as well.
        tx.execute(status -> {
          assertThat(writer.advanceBeforeMutation()).isEqualTo(2L);
          status.setRollbackOnly();
          return null;
        });
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM yak_semantic_publication_revision WHERE project_id = ?",
            Long.class, project)).isZero();

        Long firstCommittedRevision = tx.execute(status -> writer.advanceBeforeMutation());
        assertThat(firstCommittedRevision).isEqualTo(2L);
        assertThat(jdbc.queryForObject(select, Long.class, project)).isEqualTo(2L);
        // A semantic edit and subsequent revert still advance the revision.
        Long afterMutation = tx.execute(status -> writer.advanceBeforeMutation());
        assertThat(afterMutation).isEqualTo(3L);
        Long afterRevert = tx.execute(status -> writer.advanceBeforeMutation());
        assertThat(afterRevert).isEqualTo(4L);
        assertThat(jdbc.queryForObject(select, Long.class, project)).isEqualTo(4L);
        // A rejected edit cannot consume a committed revision.
        tx.execute(status -> {
          assertThat(writer.advanceBeforeMutation()).isEqualTo(5L);
          status.setRollbackOnly();
          return null;
        });
        assertThat(jdbc.queryForObject(select, Long.class, project)).isEqualTo(4L);
      } finally {
        jdbc.update("DELETE FROM yak_semantic_publication_revision WHERE project_id = ?",
            project);
      }
    });
  }

  @Test
  void b2ConcurrentSemanticWritersSerializePerProjectRevision() {
    startAndValidate(true, context -> {
      var business = context.getBean("yakBusinessDataSource", DataSource.class);
      var jdbc = new org.springframework.jdbc.core.JdbcTemplate(business);
      var tx = new org.springframework.transaction.support.TransactionTemplate(
          context.getBean("yakBusinessTransactionManager",
              org.springframework.transaction.PlatformTransactionManager.class));
      long project = (java.util.UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE) + 1;
      var trusted = org.mockito.Mockito.mock(io.yak.ops.core.project.CurrentProject.class);
      org.mockito.Mockito.when(trusted.requireProjectId()).thenReturn(project);
      var writer = new io.yak.ops.business.semantic.repository.SemanticPublicationRevisionService(
          business, trusted);
      var firstLock = new java.util.concurrent.CountDownLatch(1);
      var release = new java.util.concurrent.CountDownLatch(1);
      try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
        var first = workers.submit(() -> tx.execute(status -> {
          long revision = writer.advanceBeforeMutation();
          firstLock.countDown();
          await(release);
          return revision;
        }));
        assertThat(firstLock.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        var second = workers.submit(() ->
            tx.execute(status -> writer.advanceBeforeMutation()));
        release.countDown();
        assertThat(first.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(2L);
        assertThat(second.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(3L);
        assertThat(jdbc.queryForObject(
            "SELECT revision_no FROM yak_semantic_publication_revision WHERE project_id = ?",
            Long.class, project)).isEqualTo(3L);
      } catch (Exception failure) {
        throw new AssertionError("Semantic revision writers were not serialized", failure);
      } finally {
        release.countDown();
        jdbc.update("DELETE FROM yak_semantic_publication_revision WHERE project_id = ?",
            project);
      }
    });
  }

  @Test
  void adsV9ContractAndImmutableRevisionsRollBackAtomicallyOnMySql() {
    startAndValidate(true, context -> AdsApplicationContractSqlSmoke.verify(
        new org.springframework.jdbc.core.JdbcTemplate(
            context.getBean("yakBusinessDataSource", DataSource.class)),
        context.getBean("yakBusinessTransactionManager",
            org.springframework.transaction.PlatformTransactionManager.class)));
  }

  @Test
  void dwsGrainV7MigratesAndPersistsAcrossTransactionsOnMySql() {
    startAndValidate(true, context -> DwsGrainSqlSmoke.verify(
        new org.springframework.jdbc.core.JdbcTemplate(
            context.getBean("yakBusinessDataSource", DataSource.class)),
        context.getBean("yakBusinessTransactionManager",
            org.springframework.transaction.PlatformTransactionManager.class),
        990001L));
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
  void dwdFrozenRuntimeAttemptV3IsDurableAndNeverReplaysUnknownSqlOnMySql() {
    startAndValidate(true, context -> DwdTaskBindingSqlSmoke.verifyFrozenAttemptForwardMigration(
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
      assertThat(context.getBeansOfType(
          io.yak.ops.business.development.handoff.DevelopmentModelTaskBindingCoordinator.class))
          .isEmpty();
      assertThat(context.getBeansOfType(
          io.yak.ops.business.development.controller.v1.DevelopmentModelTaskBindingController.class))
          .isEmpty();
      assertThat(context.getBeansOfType(
          io.yak.ops.business.development.handoff.FrozenDwdExecutionCandidateService.class))
          .isEmpty();
      assertThat(context.getBeansOfType(
          io.yak.ops.business.development.handoff.DwdFrozenExecutionReservationCoordinator.class))
          .isEmpty();
      assertThat(context.getBeansOfType(
          io.yak.ops.business.development.handoff.DwdFrozenExecutionDispatchCoordinator.class))
          .isEmpty();
      assertThat(context.getBeansOfType(
          io.yak.ops.business.development.controller.v1.DwdFrozenExecutionController.class))
          .isEmpty();
      assertThat(context.getBean("yakBusinessDataSource", DataSource.class)).isNotNull();
      assertThat(context.getBean("opsDataSource")).isSameAs(context.getBean("yakBusinessDataSource"));
      Map<String, Flyway> migrations = context.getBeansOfType(Flyway.class);
      assertThat(migrations).isNotEmpty();
      migrations.values().forEach(Flyway::validate);
      acceptance.accept(context);
    }
  }
}
