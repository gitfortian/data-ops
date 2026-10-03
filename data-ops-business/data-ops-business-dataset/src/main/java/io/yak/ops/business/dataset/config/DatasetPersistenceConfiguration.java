package io.yak.ops.business.dataset.config;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
@MapperScan(
    basePackages = "io.yak.ops.business.dataset.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class DatasetPersistenceConfiguration {

  @Bean(name = "yakDatasetFlyway", initMethod = "migrate")
  public Flyway datasetFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-dataset")
        .table("flyway_schema_history_dataset")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
