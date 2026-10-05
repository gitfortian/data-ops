package io.yak.ops.business.metadata.config;

import io.yak.framework.common.jdbc.JdbcDatabase;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata persistence and Flyway configuration (self-owned migration chain).
 *
 * <p>The catalog columns of the shared table {@code yak_metadata_asset} are migrated by the
 * lineage chain, not here — see module ARCHITECTURE.md "共表列 steward 契约".
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMetadataPersistence
@MapperScan(
    basePackages = "io.yak.ops.business.metadata.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class MetadataPersistenceConfiguration {

  @Bean(name = "yakMetadataFlyway", initMethod = "migrate")
  public Flyway metadataFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations(JdbcDatabase.migrationLocation(dataSource, "classpath:db/migration/yak-metadata"))
        .table("flyway_schema_history_metadata")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
