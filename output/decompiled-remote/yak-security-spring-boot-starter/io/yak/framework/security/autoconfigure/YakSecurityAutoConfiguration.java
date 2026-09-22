/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.beans.factory.ListableBeanFactory
 *  org.springframework.beans.factory.ObjectProvider
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnBean
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication$Type
 *  org.springframework.boot.context.properties.EnableConfigurationProperties
 *  org.springframework.context.annotation.Bean
 *  org.springframework.context.annotation.Configuration
 *  org.springframework.context.annotation.Import
 */
package io.yak.framework.security.autoconfigure;

import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.framework.security.autoconfigure.YakSecurityAuditConfiguration;
import io.yak.framework.security.autoconfigure.YakSecurityDatabaseConfiguration;
import io.yak.framework.security.autoconfigure.YakSecurityOpenApiConfiguration;
import io.yak.framework.security.autoconfigure.YakSecurityWebConfiguration;
import io.yak.framework.security.bootstrap.YakSecurityBootstrapInitializer;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.context.CurrentUser;
import io.yak.framework.security.context.DefaultCurrentUser;
import io.yak.framework.security.context.YakSecurityContextFilter;
import io.yak.framework.security.dao.PermissionDao;
import io.yak.framework.security.dao.UserRoleDao;
import io.yak.framework.security.extend.OperationLogExtend;
import io.yak.framework.security.extend.PasswordEncoder;
import io.yak.framework.security.extend.PermissionExtend;
import io.yak.framework.security.extend.ResourceExtend;
import io.yak.framework.security.extend.impl.DefaultPasswordEncoder;
import io.yak.framework.security.extend.impl.DefaultPermissionExtend;
import io.yak.framework.security.extend.impl.DefaultResourceExtendImpl;
import io.yak.framework.security.extend.impl.NoOpOperationLogExtend;
import io.yak.framework.security.permission.PermissionRegistrationInitializer;
import io.yak.framework.security.permission.PermissionRegistrationService;
import io.yak.framework.security.service.RolePermissionService;
import io.yak.framework.security.service.RoleService;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.service.impl.AuthorizationSnapshotService;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(value={YakSecurityProperties.class})
@ConditionalOnProperty(prefix="yak.security", name={"enabled"}, havingValue="true", matchIfMissing=true)
@Import(value={ExtensionConfiguration.class, YakSecurityDatabaseConfiguration.class, YakSecurityWebConfiguration.class, YakSecurityAuditConfiguration.class, YakSecurityOpenApiConfiguration.class})
public class YakSecurityAutoConfiguration {
    @Bean
    @ConditionalOnBean(value={PermissionDao.class})
    @ConditionalOnProperty(prefix="yak.security.permission-registration", name={"enabled"}, havingValue="true", matchIfMissing=true)
    PermissionRegistrationService yakPermissionRegistrationService(PermissionDao permissionDao) {
        return new PermissionRegistrationService(permissionDao);
    }

    @Bean
    @ConditionalOnBean(value={PermissionDao.class})
    @ConditionalOnProperty(prefix="yak.security.permission-registration", name={"enabled"}, havingValue="true", matchIfMissing=true)
    PermissionRegistrationInitializer yakPermissionRegistrationInitializer(ListableBeanFactory beanFactory, PermissionRegistrationService registrationService) {
        return new PermissionRegistrationInitializer(beanFactory, registrationService);
    }

    @Bean
    @ConditionalOnBean(value={UserService.class, RoleService.class, RolePermissionService.class, PermissionDao.class})
    @ConditionalOnProperty(prefix="yak.security.bootstrap", name={"enabled"}, havingValue="true")
    YakSecurityBootstrapInitializer yakSecurityBootstrapInitializer(YakSecurityProperties properties, UserService userService, RoleService roleService, RolePermissionService rolePermissionService, PermissionDao permissionDao) {
        return new YakSecurityBootstrapInitializer(properties, userService, roleService, rolePermissionService, permissionDao);
    }

    static class ExtensionConfiguration {
        ExtensionConfiguration() {
        }

        @Bean
        @ConditionalOnMissingBean(value={PasswordEncoder.class})
        PasswordEncoder passwordEncoder() {
            return new DefaultPasswordEncoder();
        }

        @Bean
        @ConditionalOnMissingBean
        PermissionExtend permissionExtend() {
            return new DefaultPermissionExtend();
        }

        @Bean
        @ConditionalOnMissingBean(value={CurrentUser.class})
        CurrentUser currentUser() {
            return new DefaultCurrentUser();
        }

        @Bean
        @ConditionalOnMissingBean(value={YakSecurityContextFilter.class})
        @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
        YakSecurityContextFilter yakSecurityContextFilter(ObjectProvider<UserRoleDao> userRoleDaoProvider, ObjectProvider<AuthenticationManager> authenticationManagerProvider, ObjectProvider<AuthorizationSnapshotService> authorizationSnapshotServiceProvider) {
            return new YakSecurityContextFilter(userRoleDaoProvider, authenticationManagerProvider, authorizationSnapshotServiceProvider);
        }

        @Bean
        @ConditionalOnMissingBean
        OperationLogExtend operationLogExtend() {
            return new NoOpOperationLogExtend();
        }

        @Bean
        @ConditionalOnMissingBean
        ResourceExtend resourceExtend() {
            return new DefaultResourceExtendImpl();
        }
    }
}

