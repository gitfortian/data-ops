package io.yak.ops.boot.config.persistence;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.framework.common.jdbc.JdbcDatabase;
import javax.sql.DataSource;
import java.util.Properties;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Single assembly boundary for business MyBatis SqlSessionFactory and SqlSessionTemplate.
 *
 * Keeps the original dialect mapping, PostgreSQL type handler, mapper resources and bean aliases.
 * The runtime is intentionally not replaced with Starter auto-configuration in this behavior-free step.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "yak.database",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BusinessMybatisSessionConfiguration {
    @Primary
    @Bean(
            name = {
                    "yakBusinessSqlSessionFactory",
                    "opsDataSourceSqlSessionFactory",
                    "opsResourceSqlSessionFactory",
                    "offlineSyncSqlSessionFactory"
            })
    public SqlSessionFactory yakBusinessSqlSessionFactory(
            @Qualifier("yakBusinessDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        VendorDatabaseIdProvider databaseIds = new VendorDatabaseIdProvider();
        Properties vendors = new Properties();
        vendors.setProperty("PostgreSQL", "postgresql");
        vendors.setProperty("MySQL", "mysql");
        vendors.setProperty("MariaDB", "mysql");
        databaseIds.setProperties(vendors);
        factory.setDatabaseIdProvider(databaseIds);

        factory.setTypeAliasesPackage("io.yak.ops.business.**.dao.model");

        // All Yak Ops business modules share this SqlSessionFactory. Each module keeps its XML files
        // under mapper/<domain>/ so complex SQL stays close to the owning business module.
        Resource[] mapperLocations = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/**/*.xml");
        if (mapperLocations.length > 0) {
            factory.setMapperLocations(mapperLocations);
        }

        var configuration = MybatisPlusFactorySupport.createConfiguration();
        if (JdbcDatabase.isPostgresql(dataSource)) {
            configuration.getTypeHandlerRegistry().register(Boolean.class, NumericBooleanTypeHandler.class);
            configuration.getTypeHandlerRegistry().register(boolean.class, NumericBooleanTypeHandler.class);
        }
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(MybatisPlusFactorySupport.createGlobalConfig());

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        factory.setPlugins(interceptor);
        return factory.getObject();
    }

    @Primary
    @Bean(
            name = {
                    "yakBusinessSqlSessionTemplate",
                    "opsDataSourceSqlSessionTemplate",
                    "opsResourceSqlSessionTemplate",
                    "offlineSyncSqlSessionTemplate"
            })
    public SqlSessionTemplate yakBusinessSqlSessionTemplate(
            @Qualifier("yakBusinessSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
