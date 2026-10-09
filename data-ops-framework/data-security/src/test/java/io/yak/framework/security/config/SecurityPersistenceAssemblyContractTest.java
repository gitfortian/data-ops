package io.yak.framework.security.config;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A8.2 consolidated migration: Spring/MyBatis wiring regression without a live database.
 * Covers concrete runtime object creation in addition to the static owner guard.
 */
class SecurityPersistenceAssemblyContractTest {

    @Test
    void mapperScanningUsesTheSecuritySqlSessionTemplate() {
        MapperScan scan = DataSourceConfig.class.getAnnotation(MapperScan.class);
        assertNotNull(scan);
        assertArrayEquals(new String[] {"io.yak.framework.security.dao.mapper"},
                scan.basePackages());
        assertEquals("yakSecuritySqlSessionTemplate", scan.sqlSessionTemplateRef());
    }

    @Test
    void globalConfigRetainsApplicationInsertFillAndAutoIds() {
        YakSecurityProperties properties = new YakSecurityProperties();
        properties.setApplicationName("a82_contract");
        GlobalConfig globalConfig = new DataSourceConfig().yakSecurityGlobalConfig(properties);
        assertNotNull(globalConfig.getMetaObjectHandler());
        assertEquals(com.baomidou.mybatisplus.annotation.IdType.AUTO,
                globalConfig.getDbConfig().getIdType());
    }

    @Test
    void tenantIsolationRunsAheadOfPagination() {
        YakSecurityProperties properties = new YakSecurityProperties();
        properties.setApplicationName("a82_contract");
        MybatisPlusInterceptor plugin =
                new DataSourceConfig().yakSecurityMybatisPlusInterceptor(properties);
        assertEquals(2, plugin.getInterceptors().size());
        assertInstanceOf(TenantLineInnerInterceptor.class, plugin.getInterceptors().get(0));
        assertInstanceOf(PaginationInnerInterceptor.class, plugin.getInterceptors().get(1));
    }

    @Test
    void sqlSessionFactoryDependsOnFlywayAndRetainsQualifiedBindings() throws Exception {
        Method method = DataSourceConfig.class.getMethod("yakSecuritySqlSessionFactory",
                javax.sql.DataSource.class, GlobalConfig.class, MybatisPlusInterceptor.class);
        DependsOn dependency = method.getAnnotation(DependsOn.class);
        assertNotNull(dependency);
        assertArrayEquals(new String[] {"yakSecurityFlyway"}, dependency.value());
        assertEquals("yakSecuritySqlSessionFactory", method.getAnnotation(Bean.class).value()[0]);
        assertEquals("yakSecurityDataSource",
                method.getParameters()[0].getAnnotation(Qualifier.class).value());
        assertEquals("yakSecurityGlobalConfig",
                method.getParameters()[1].getAnnotation(Qualifier.class).value());
        assertEquals("yakSecurityMybatisPlusInterceptor",
                method.getParameters()[2].getAnnotation(Qualifier.class).value());
    }

    @Test
    void missingApplicationNameFailsClosedBeforeConstructingTenantPlugin() {
        YakSecurityProperties properties = new YakSecurityProperties();
        assertThrows(IllegalStateException.class,
                () -> new DataSourceConfig().yakSecurityMybatisPlusInterceptor(properties));
        assertThrows(IllegalStateException.class,
                () -> new DataSourceConfig().yakSecurityGlobalConfig(properties));
    }
}
