package io.yak.ops.business.dashboard.config;

import io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

/** Dashboard 数据库迁移配置。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
@EnableConfigurationProperties(DataSourceProperties.class)
@Import(BusinessDatabaseConfiguration.class)
public class DashboardPersistenceConfiguration {

    @Bean(name = "yakDashboardFlyway", initMethod = "migrate")
    @DependsOn("yakAnalysisFlyway")
    public Flyway dashboardFlyway(
            @Qualifier("yakBusinessDataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/yak-dashboard")
                .table("flyway_schema_history_dashboard")
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .baselineOnMigrate(true)
                .load();
    }
}
