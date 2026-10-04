package io.yak.ops.business.dashboard.config;

import io.yak.framework.common.jdbc.JdbcDatabase;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/** Dashboard 数据库迁移配置。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
@MapperScan(
        basePackages = "io.yak.ops.business.dashboard.dao.mapper",
        sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class DashboardPersistenceConfiguration {

    @Bean(name = "yakDashboardFlyway", initMethod = "migrate")
    @DependsOn("yakAnalysisFlyway")
    public Flyway dashboardFlyway(
            @Qualifier("yakBusinessDataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(JdbcDatabase.migrationLocation(dataSource, "classpath:db/migration/yak-dashboard"))
                .table("flyway_schema_history_dashboard")
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .baselineOnMigrate(true)
                .load();
    }
}
