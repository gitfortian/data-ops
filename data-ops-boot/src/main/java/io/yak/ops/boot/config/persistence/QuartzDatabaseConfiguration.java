package io.yak.ops.boot.config.persistence;

import io.yak.framework.common.jdbc.JdbcDatabase;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Assemble durable Quartz storage without the destructive Boot initializer. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.quartz", name = "job-store-type", havingValue = "jdbc")
public class QuartzDatabaseConfiguration {
  @Bean(name = "yakQuartzFlyway", initMethod = "migrate")
  public Flyway yakQuartzFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure().dataSource(dataSource)
        .locations(JdbcDatabase.migrationLocation(dataSource, "classpath:db/migration/yak-quartz"))
        .table("yak_quartz_schema_history")
        .baselineOnMigrate(true).baselineVersion(MigrationVersion.fromVersion("0")).load();
  }

  @Bean
  public SchedulerFactoryBeanCustomizer quartzDatabaseReady(
      @Qualifier("yakQuartzFlyway") Flyway migrations) {
    // Resolving this dependency completes migration before SchedulerFactoryBean starts Quartz.
    return factory -> {};
  }
}
