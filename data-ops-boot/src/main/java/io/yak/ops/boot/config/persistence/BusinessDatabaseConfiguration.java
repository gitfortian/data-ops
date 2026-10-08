package io.yak.ops.boot.config.persistence;

import io.yak.framework.common.jdbc.JdbcDatabase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Owns the primary business DataSource and transaction manager.
 *
 * MyBatis sessions are assembled separately, but retain all historical bean names and semantics.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "yak.database",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(BusinessDatabaseProperties.class)
@Import(BusinessMybatisSessionConfiguration.class)
public class BusinessDatabaseConfiguration {

    @Primary
    @Bean(
            name = {
                    "yakBusinessDataSource",
                    "opsDataSource",
                    "opsResourceDataSource",
                    "offlineSyncDataSource"
            },
            destroyMethod = "close")
    public HikariDataSource yakBusinessDataSource(BusinessDatabaseProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("YakBusinessDatabasePool");
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());
        config.setMinimumIdle(properties.getMinimumIdle());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setAutoCommit(true);
        if (JdbcDatabase.isPostgresql(properties.getUrl())) {
            config.addDataSourceProperty("stringtype", "unspecified");
        }
        return new HikariDataSource(config);
    }

    @Primary
    @Bean(
            name = {
                    "yakBusinessTransactionManager",
                    "opsDataSourceTransactionManager",
                    "opsResourceTransactionManager",
                    "offlineSyncTransactionManager"
            })
    public PlatformTransactionManager yakBusinessTransactionManager(
            @Qualifier("yakBusinessDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

}
