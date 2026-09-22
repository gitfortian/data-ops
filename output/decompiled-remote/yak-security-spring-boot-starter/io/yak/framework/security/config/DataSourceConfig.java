/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.alibaba.druid.pool.DruidDataSource
 *  com.baomidou.mybatisplus.annotation.DbType
 *  com.baomidou.mybatisplus.annotation.IdType
 *  com.baomidou.mybatisplus.core.MybatisConfiguration
 *  com.baomidou.mybatisplus.core.config.GlobalConfig
 *  com.baomidou.mybatisplus.core.config.GlobalConfig$DbConfig
 *  com.baomidou.mybatisplus.core.handlers.MetaObjectHandler
 *  com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor
 *  com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler
 *  com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor
 *  com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
 *  com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor
 *  com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean
 *  net.sf.jsqlparser.expression.Expression
 *  net.sf.jsqlparser.expression.StringValue
 *  org.apache.ibatis.plugin.Interceptor
 *  org.apache.ibatis.session.SqlSessionFactory
 *  org.flywaydb.core.Flyway
 *  org.flywaydb.core.api.MigrationVersion
 *  org.mybatis.spring.SqlSessionTemplate
 *  org.mybatis.spring.annotation.MapperScan
 *  org.springframework.beans.factory.annotation.Qualifier
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnClass
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
 *  org.springframework.boot.context.properties.EnableConfigurationProperties
 *  org.springframework.context.annotation.Bean
 *  org.springframework.context.annotation.Configuration
 *  org.springframework.context.annotation.DependsOn
 *  org.springframework.jdbc.datasource.DataSourceTransactionManager
 *  org.springframework.transaction.PlatformTransactionManager
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.framework.security.config.YakSecurityMetaObjectHandler;
import io.yak.framework.security.config.YakSecurityProperties;
import java.util.Collections;
import javax.sql.DataSource;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(value={YakSecurityProperties.class})
@ConditionalOnClass(value={DataSource.class, SqlSessionFactory.class, MybatisSqlSessionFactoryBean.class, Flyway.class})
@ConditionalOnProperty(prefix="yak.security", name={"database-enabled", "datasource.enabled"}, havingValue="true", matchIfMissing=true)
@MapperScan(basePackages={"io.yak.framework.security.dao.mapper"}, sqlSessionTemplateRef="yakSecuritySqlSessionTemplate")
public class DataSourceConfig {
    static final String FLYWAY_MIGRATION_LOCATION = "classpath:yak-security/db/migration";

    @Bean(value={"yakSecurityGlobalConfig"})
    public GlobalConfig yakSecurityGlobalConfig(YakSecurityProperties properties) {
        DataSourceConfig.requireText(properties.getApplicationName(), "yak.security.application-name");
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        globalConfig.setMetaObjectHandler((MetaObjectHandler)new YakSecurityMetaObjectHandler(properties.getApplicationName()));
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        dbConfig.setIdType(IdType.AUTO);
        globalConfig.setDbConfig(dbConfig);
        return globalConfig;
    }

    @Bean(name={"yakSecurityDataSource"}, destroyMethod="close")
    public DataSource yakSecurityDataSource(YakSecurityProperties properties) {
        YakSecurityProperties.DataSourceProperties datasource = properties.getDatasource();
        if (datasource == null) {
            throw new IllegalStateException("Missing required configuration: yak.security.datasource");
        }
        DataSourceConfig.requireText(datasource.getUrl(), "yak.security.datasource.url");
        DataSourceConfig.requireText(datasource.getUsername(), "yak.security.datasource.username");
        DataSourceConfig.requireText(datasource.getDriverClassName(), "yak.security.datasource.driver-class-name");
        DruidDataSource result = new DruidDataSource();
        result.setUrl(datasource.getUrl());
        result.setUsername(datasource.getUsername());
        result.setPassword(datasource.getPassword());
        result.setDriverClassName(datasource.getDriverClassName());
        result.setInitialSize(datasource.getInitialSize());
        result.setMinIdle(datasource.getMinIdle());
        result.setMaxActive(datasource.getMaxActive());
        result.setMaxWait(datasource.getMaxWait());
        result.setValidationQuery(datasource.getValidationQuery());
        result.setTestWhileIdle(datasource.isTestWhileIdle());
        result.setTestOnBorrow(datasource.isTestOnBorrow());
        result.setTestOnReturn(datasource.isTestOnReturn());
        return result;
    }

    @Bean(value={"yakSecurityMybatisPlusInterceptor"})
    public MybatisPlusInterceptor yakSecurityMybatisPlusInterceptor(YakSecurityProperties properties) {
        DataSourceConfig.requireText(properties.getApplicationName(), "yak.security.application-name");
        final String applicationName = properties.getApplicationName();
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        TenantLineHandler tenantLineHandler = new TenantLineHandler(){

            public Expression getTenantId() {
                return new StringValue(applicationName);
            }

            public String getTenantIdColumn() {
                return "app_name";
            }
        };
        interceptor.addInnerInterceptor((InnerInterceptor)new TenantLineInnerInterceptor(tenantLineHandler));
        interceptor.addInnerInterceptor((InnerInterceptor)new PaginationInnerInterceptor(DbType.MARIADB));
        return interceptor;
    }

    @Bean(name={"yakSecurityFlyway"}, initMethod="migrate")
    public Flyway yakSecurityFlyway(@Qualifier(value="yakSecurityDataSource") DataSource dataSource, YakSecurityProperties properties) {
        DataSourceConfig.requireText(properties.getApplicationName(), "yak.security.application-name");
        return Flyway.configure().dataSource(dataSource).locations(new String[]{FLYWAY_MIGRATION_LOCATION}).placeholders(Collections.singletonMap("appName", properties.getApplicationName())).baselineOnMigrate(true).baselineVersion(MigrationVersion.fromVersion((String)"0")).outOfOrder(true).load();
    }

    @Bean(value={"yakSecuritySqlSessionFactory"})
    @DependsOn(value={"yakSecurityFlyway"})
    public SqlSessionFactory yakSecuritySqlSessionFactory(@Qualifier(value="yakSecurityDataSource") DataSource dataSource, @Qualifier(value="yakSecurityGlobalConfig") GlobalConfig globalConfig, @Qualifier(value="yakSecurityMybatisPlusInterceptor") MybatisPlusInterceptor interceptor) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(globalConfig);
        factory.setPlugins(new Interceptor[]{interceptor});
        SqlSessionFactory sqlSessionFactory = factory.getObject();
        if (sqlSessionFactory == null) {
            throw new IllegalStateException("Failed to create yakSecuritySqlSessionFactory");
        }
        return sqlSessionFactory;
    }

    @Bean(value={"yakSecuritySqlSessionTemplate"})
    public SqlSessionTemplate yakSecuritySqlSessionTemplate(@Qualifier(value="yakSecuritySqlSessionFactory") SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

    @Bean(value={"yakSecurityTransactionManager"})
    public PlatformTransactionManager yakSecurityTransactionManager(@Qualifier(value="yakSecurityDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    private static void requireText(String value, String key) {
        if (!StringUtils.hasText((String)value)) {
            throw new IllegalStateException("Missing required configuration: " + key);
        }
    }
}

