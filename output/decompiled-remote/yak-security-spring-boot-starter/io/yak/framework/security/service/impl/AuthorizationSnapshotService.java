/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.entity.Permission;
import io.yak.framework.security.context.AuthorizationSnapshot;
import io.yak.framework.security.dao.PermissionDao;
import io.yak.framework.security.service.PermissionCache;
import io.yak.framework.security.service.RolePermissionService;
import io.yak.framework.security.service.UserProjectService;
import io.yak.framework.security.service.UserRoleService;
import io.yak.framework.security.service.impl.UserMenuGrantService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class AuthorizationSnapshotService {
    private final PermissionCache permissionCache;
    private final UserRoleService userRoleService;
    private final RolePermissionService rolePermissionService;
    private final PermissionDao permissionDao;
    private final UserMenuGrantService userMenuGrantService;
    private final UserProjectService userProjectService;

    public AuthorizationSnapshotService(PermissionCache permissionCache, UserRoleService userRoleService, RolePermissionService rolePermissionService, PermissionDao permissionDao, UserMenuGrantService userMenuGrantService, UserProjectService userProjectService) {
        this.permissionCache = permissionCache;
        this.userRoleService = userRoleService;
        this.rolePermissionService = rolePermissionService;
        this.permissionDao = permissionDao;
        this.userMenuGrantService = userMenuGrantService;
        this.userProjectService = userProjectService;
    }

    public AuthorizationSnapshot get(Long userId) {
        if (userId == null) {
            return AuthorizationSnapshot.empty();
        }
        return this.permissionCache.getAuthorizationSnapshot(userId, () -> this.load(userId));
    }

    private AuthorizationSnapshot load(Long userId) {
        List<Long> roleIds = this.normalizeIds(this.userRoleService.getRoleIdListByUserId(userId));
        List<Long> permissionIds = roleIds.isEmpty() ? Collections.emptyList() : this.normalizeIds(this.rolePermissionService.getPermissionIdListByRoleIdList(roleIds));
        Set<String> permissionCodes = this.loadPermissionCodes(permissionIds);
        UserMenuGrantService.MenuGrant menuGrant = this.userMenuGrantService.resolve(roleIds, permissionIds);
        permissionCodes.addAll(menuGrant.getPermissionCodes());
        Set<Long> projectIds = permissionCodes.contains("security:root") ? Collections.emptySet() : this.loadProjectIds(userId);
        return new AuthorizationSnapshot(roleIds, permissionCodes, menuGrant.getMenuCodes(), projectIds);
    }

    private Set<Long> loadProjectIds(Long userId) {
        LinkedHashSet<Long> projectIds = new LinkedHashSet<Long>();
        List<Long> assignedProjectIds = this.userProjectService.getProjectIdListByUserIdList(Collections.singletonList(userId));
        if (!CollectionUtils.isEmpty(assignedProjectIds)) {
            assignedProjectIds.stream().filter(Objects::nonNull).filter(projectId -> projectId > 0L).forEach(projectIds::add);
        }
        return projectIds;
    }

    private Set<String> loadPermissionCodes(List<Long> permissionIds) {
        if (CollectionUtils.isEmpty(permissionIds)) {
            return new LinkedHashSet<String>();
        }
        HashSet<Long> grantedIds = new HashSet<Long>(permissionIds);
        LinkedHashSet<String> permissionCodes = new LinkedHashSet<String>();
        List<Permission> permissions = this.permissionDao.selectAllAndAscOrderByLevel();
        if (CollectionUtils.isEmpty(permissions)) {
            return permissionCodes;
        }
        for (Permission permission : permissions) {
            if (permission == null || !grantedIds.contains(permission.getId()) || !Boolean.TRUE.equals(permission.getActive()) || !StringUtils.hasText((String)permission.getPermissionCode())) continue;
            permissionCodes.add(permission.getPermissionCode());
        }
        return permissionCodes;
    }

    private List<Long> normalizeIds(List<Long> values) {
        if (CollectionUtils.isEmpty(values)) {
            return new ArrayList<Long>();
        }
        return values.stream().filter(Objects::nonNull).filter(value -> value > 0L).distinct().collect(Collectors.toList());
    }
}

