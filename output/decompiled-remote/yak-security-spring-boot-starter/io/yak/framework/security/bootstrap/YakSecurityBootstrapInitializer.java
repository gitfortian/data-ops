/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.common.Result
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.boot.ApplicationArguments
 *  org.springframework.boot.ApplicationRunner
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.bootstrap;

import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.role.RoleSaveDTO;
import io.yak.framework.security.common.dto.user.UserDTO;
import io.yak.framework.security.common.entity.Permission;
import io.yak.framework.security.common.vo.role.RoleBriefVO;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.dao.PermissionDao;
import io.yak.framework.security.service.RolePermissionService;
import io.yak.framework.security.service.RoleService;
import io.yak.framework.security.service.UserService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

public class YakSecurityBootstrapInitializer
implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(YakSecurityBootstrapInitializer.class);
    private static final String ADMINISTRATOR_ROLE = "\u7cfb\u7edf\u7ba1\u7406\u5458";
    private static final String BOOTSTRAP_OPERATOR = "yak-security-bootstrap";
    private final YakSecurityProperties properties;
    private final UserService userService;
    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;
    private final PermissionDao permissionDao;

    public YakSecurityBootstrapInitializer(YakSecurityProperties properties, UserService userService, RoleService roleService, RolePermissionService rolePermissionService, PermissionDao permissionDao) {
        this.properties = properties;
        this.userService = userService;
        this.roleService = roleService;
        this.rolePermissionService = rolePermissionService;
        this.permissionDao = permissionDao;
    }

    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void run(ApplicationArguments args) {
        if (!this.userService.getAllUserBriefList().isEmpty()) {
            return;
        }
        YakSecurityProperties.BootstrapProperties bootstrap = this.properties.getBootstrap();
        YakSecurityBootstrapInitializer.requireText(bootstrap.getUsername(), "username");
        YakSecurityBootstrapInitializer.requireText(bootstrap.getPassword(), "password");
        YakSecurityBootstrapInitializer.requireText(bootstrap.getRealName(), "real-name");
        List<Long> permissionIds = this.permissionDao.selectAllAndAscOrderByLevel().stream().map(Permission::getId).toList();
        if (permissionIds.isEmpty()) {
            throw new IllegalStateException("Cannot bootstrap Yak Security: no built-in permissions exist");
        }
        RoleBriefVO administratorRole = this.roleService.getRoleBriefListByRoleName(ADMINISTRATOR_ROLE).stream().findFirst().orElseGet(() -> this.createAdministratorRole(permissionIds));
        this.rolePermissionService.updateRolePermission(administratorRole.getId(), permissionIds);
        UserDTO administrator = new UserDTO();
        administrator.setUserName(bootstrap.getUsername());
        administrator.setPw(bootstrap.getPassword());
        administrator.setRealName(bootstrap.getRealName());
        administrator.setRoleIds(List.of(administratorRole.getId()));
        Result<Void> result = this.userService.addUser(administrator, BOOTSTRAP_OPERATOR);
        if (result.failed()) {
            throw new IllegalStateException("Cannot bootstrap Yak Security administrator: " + result.getMessage());
        }
        LOGGER.warn("Yak Security bootstrap administrator '{}' was created. Disable yak.security.bootstrap.enabled now.", (Object)bootstrap.getUsername());
    }

    private RoleBriefVO createAdministratorRole(List<Long> permissionIds) {
        RoleSaveDTO role = new RoleSaveDTO();
        role.setRoleName(ADMINISTRATOR_ROLE);
        role.setDescription("Yak Security bootstrap administrator role");
        role.setPermissionIdList(permissionIds);
        this.roleService.createRole(role, BOOTSTRAP_OPERATOR);
        return (RoleBriefVO)this.roleService.getRoleBriefListByRoleName(ADMINISTRATOR_ROLE).stream().findFirst().orElseThrow(() -> new IllegalStateException("Cannot bootstrap Yak Security: administrator role was not created"));
    }

    private static void requireText(String value, String property) {
        if (!StringUtils.hasText((String)value)) {
            throw new IllegalStateException("yak.security.bootstrap." + property + " must be configured when bootstrap is enabled");
        }
    }
}

