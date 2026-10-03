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
        Map.entry("yak.security.permission-registration.enabled", "false"),
        Map.entry("yak.security.bootstrap.enabled", "false"),
        Map.entry("spring.quartz.auto-startup", "false"));
    try (var context = new SpringApplicationBuilder(YakOpsApplication.class)
        .initializers(application -> application.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("isolated-migration-smoke", properties))).run()) {
      assertThat(context.getBean("yakBusinessDataSource", DataSource.class)).isNotNull();
      assertThat(context.getBean("opsDataSource")).isSameAs(context.getBean("yakBusinessDataSource"));
      Map<String, Flyway> migrations = context.getBeansOfType(Flyway.class);
      assertThat(migrations).isNotEmpty();
      migrations.values().forEach(Flyway::validate);
    }
  }
}
