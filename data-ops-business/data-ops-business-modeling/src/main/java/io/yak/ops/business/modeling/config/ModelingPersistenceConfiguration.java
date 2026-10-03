package io.yak.ops.business.modeling.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/** Modeling persistence and Flyway configuration. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnModelingPersistence
@MapperScan(
    basePackages = "io.yak.ops.business.modeling.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class ModelingPersistenceConfiguration {

  private static final String HISTORY_TABLE = "flyway_schema_history_modeling";
  private static final String COMMON_MIGRATION_LOCATION = "classpath:db/migration/yak-modeling";

  /**
   * modeling 的迁移已按模块合并为单文件 V1__modeling_baseline.sql,
   * 不再需要按历史表在 impact / logical 两组目录之间二选一。
   */
  @Bean(name = "yakModelingFlyway", initMethod = "migrate")
  @DependsOn("yakSemanticFlyway")
  public Flyway modelingFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations(COMMON_MIGRATION_LOCATION)
        .table(HISTORY_TABLE)
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
