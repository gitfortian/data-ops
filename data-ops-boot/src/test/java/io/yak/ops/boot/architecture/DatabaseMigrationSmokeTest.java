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
