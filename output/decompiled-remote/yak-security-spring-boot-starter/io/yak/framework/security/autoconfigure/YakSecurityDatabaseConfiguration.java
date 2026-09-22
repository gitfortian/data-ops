/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
 *  org.springframework.context.annotation.Configuration
 *  org.springframework.context.annotation.Import
 */
package io.yak.framework.security.autoconfigure;

import io.yak.framework.security.autoconfigure.YakSecurityAuthenticationConfiguration;
import io.yak.framework.security.config.DataSourceConfig;
import io.yak.framework.security.dao.impl.ConfigDaoImpl;
import io.yak.framework.security.dao.impl.DeptDaoImpl;
import io.yak.framework.security.dao.impl.MessageDaoImpl;
import io.yak.framework.security.dao.impl.OplogDaoImpl;
import io.yak.framework.security.dao.impl.OplogExtraDaoImpl;
import io.yak.framework.security.dao.impl.PermissionDaoImpl;
import io.yak.framework.security.dao.impl.ProjectDaoImpl;
import io.yak.framework.security.dao.impl.ResourceTypeDaoImpl;
import io.yak.framework.security.dao.impl.RoleDaoImpl;
import io.yak.framework.security.dao.impl.RolePermissionDaoImpl;
import io.yak.framework.security.dao.impl.UserDaoImpl;
import io.yak.framework.security.dao.impl.UserProjectDaoImpl;
import io.yak.framework.security.dao.impl.UserResourceDaoImpl;
import io.yak.framework.security.dao.impl.UserRoleDaoImpl;
import io.yak.framework.security.notification.DefaultNotificationPublisher;
import io.yak.framework.security.service.impl.AuthorizationSnapshotService;
import io.yak.framework.security.service.impl.CaffeinePermissionCache;
import io.yak.framework.security.service.impl.ConfigServiceImpl;
import io.yak.framework.security.service.impl.CurrentUserProjectResolver;
import io.yak.framework.security.service.impl.DeptServiceImpl;
import io.yak.framework.security.service.impl.LoginServiceImpl;
import io.yak.framework.security.service.impl.MenuAuthorizationService;
import io.yak.framework.security.service.impl.MenuAwarePermissionService;
import io.yak.framework.security.service.impl.MenuAwareRolePermissionService;
import io.yak.framework.security.service.impl.MessageServiceImpl;
import io.yak.framework.security.service.impl.OplogExtraServiceImpl;
import io.yak.framework.security.service.impl.OplogServiceImpl;
import io.yak.framework.security.service.impl.PermissionAdministrationService;
import io.yak.framework.security.service.impl.PermissionMenuRelationService;
import io.yak.framework.security.service.impl.PermissionServiceImpl;
import io.yak.framework.security.service.impl.ProjectServiceImpl;
import io.yak.framework.security.service.impl.RbacPermissionServiceImpl;
import io.yak.framework.security.service.impl.ResourceTypeServiceImpl;
import io.yak.framework.security.service.impl.RolePermissionServiceImpl;
import io.yak.framework.security.service.impl.RoleServiceImpl;
import io.yak.framework.security.service.impl.UserAdministrationService;
import io.yak.framework.security.service.impl.UserMenuGrantService;
import io.yak.framework.security.service.impl.UserProjectServiceImpl;
import io.yak.framework.security.service.impl.UserResourceServiceImpl;
import io.yak.framework.security.service.impl.UserRoleServiceImpl;
import io.yak.framework.security.service.impl.UserServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="yak.security", name={"database-enabled"}, havingValue="true", matchIfMissing=true)
@Import(value={DataSourceConfig.class, ConfigDaoImpl.class, DeptDaoImpl.class, MessageDaoImpl.class, OplogDaoImpl.class, OplogExtraDaoImpl.class, PermissionDaoImpl.class, ProjectDaoImpl.class, ResourceTypeDaoImpl.class, RoleDaoImpl.class, RolePermissionDaoImpl.class, UserDaoImpl.class, UserProjectDaoImpl.class, UserResourceDaoImpl.class, UserRoleDaoImpl.class, CaffeinePermissionCache.class, ConfigServiceImpl.class, DeptServiceImpl.class, LoginServiceImpl.class, MenuAuthorizationService.class, PermissionMenuRelationService.class, MenuAwarePermissionService.class, MenuAwareRolePermissionService.class, UserMenuGrantService.class, CurrentUserProjectResolver.class, MessageServiceImpl.class, DefaultNotificationPublisher.class, OplogExtraServiceImpl.class, OplogServiceImpl.class, PermissionAdministrationService.class, PermissionServiceImpl.class, ProjectServiceImpl.class, AuthorizationSnapshotService.class, RbacPermissionServiceImpl.class, ResourceTypeServiceImpl.class, RolePermissionServiceImpl.class, RoleServiceImpl.class, UserProjectServiceImpl.class, UserAdministrationService.class, UserResourceServiceImpl.class, UserRoleServiceImpl.class, UserServiceImpl.class, YakSecurityAuthenticationConfiguration.class})
class YakSecurityDatabaseConfiguration {
    YakSecurityDatabaseConfiguration() {
    }
}

