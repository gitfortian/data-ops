package io.yak.ops.business.asset.config;

import io.yak.framework.common.jdbc.JdbcDatabase;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Asset persistence and Flyway configuration (self-owned migration chain). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAssetPersistence
@MapperScan(
    basePackages = "io.yak.ops.business.asset.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class AssetPersistenceConfiguration {

  @Bean(name = "yakAssetFlyway", initMethod = "migrate")
  public Flyway assetFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations(JdbcDatabase.migrationLocation(dataSource, "classpath:db/migration/yak-asset"))
        .table("flyway_schema_history_asset")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
