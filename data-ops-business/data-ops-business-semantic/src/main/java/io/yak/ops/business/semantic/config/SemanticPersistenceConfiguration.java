package io.yak.ops.business.semantic.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Semantic persistence and Flyway configuration (self-owned migration chain). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnSemanticPersistence
@MapperScan(
    basePackages = "io.yak.ops.business.semantic.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class SemanticPersistenceConfiguration {

  @Bean(name = "yakSemanticFlyway", initMethod = "migrate")
  public Flyway semanticFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-semantic")
        .table("flyway_schema_history_semantic")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
