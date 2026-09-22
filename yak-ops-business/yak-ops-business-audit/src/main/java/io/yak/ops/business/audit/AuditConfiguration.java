package io.yak.ops.business.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.core.project.CurrentProject;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Wires the shared audit store onto the Yak Ops business database. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuditConfiguration {

  private static final Logger log = LoggerFactory.getLogger(AuditConfiguration.class);

  @Bean
  public Flyway yakAuditFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/yak-audit")
            .table("yak_audit_schema_history")
            .baselineVersion(MigrationVersion.fromVersion("0"))
            .baselineOnMigrate(true)
            .load();
    try {
      flyway.migrate();
    } catch (RuntimeException exception) {
      log.warn("Audit schema migration failed; audit writes will remain fail-open", exception);
    }
    return flyway;
  }

  @Bean
  public BusinessAuditService businessAuditService(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager,
      CurrentProject currentProject,
      ObjectMapper objectMapper,
      ObjectProvider<AuditActorResolver> actorResolverProvider) {
    AuditActorResolver actorResolver =
        actorResolverProvider.getIfAvailable(SpringSecurityAuditActorResolver::new);
    return new JdbcBusinessAuditService(
        dataSource, transactionManager, currentProject, objectMapper, actorResolver);
  }

  @Bean
  public AuditQueryService auditQueryService(
      @Qualifier("yakBusinessDataSource") DataSource dataSource, ObjectMapper objectMapper) {
    return new JdbcAuditQueryService(
        dataSource, objectMapper, new AuditTimelineRendererRegistry());
  }
}
