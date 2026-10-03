package io.yak.ops.business.job.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobRuntimeFlywayConfiguration {
  @Bean(name = "yakJobRuntimeFlyway", initMethod = "migrate")
  public Flyway jobRuntimeFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/yak-job-runtime")
        .table("flyway_schema_history_job_runtime").baselineVersion("0").baselineOnMigrate(true).load();
  }
}
