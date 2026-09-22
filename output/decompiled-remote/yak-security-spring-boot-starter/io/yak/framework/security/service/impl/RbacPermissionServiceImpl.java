/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.context.AuthorizationSnapshot;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.framework.security.extend.PermissionExtend;
import io.yak.framework.security.service.RbacPermissionService;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.service.impl.AuthorizationSnapshotService;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RbacPermissionServiceImpl
implements RbacPermissionService {
    private final UserService userService;
    private final PermissionExtend permissionExtend;
    private final AuthorizationSnapshotService authorizationSnapshotService;

    public RbacPermissionServiceImpl(UserService userService, PermissionExtend permissionExtend, AuthorizationSnapshotService authorizationSnapshotService) {
        this.userService = userService;
        this.permissionExtend = permissionExtend;
        this.authorizationSnapshotService = authorizationSnapshotService;
    }

    @Override
    public boolean hasPermission(String userName, String permissionCode) {
        AuthorizationSnapshot snapshot;
        if (!StringUtils.hasText((String)userName) || !StringUtils.hasText((String)permissionCode)) {
            return false;
        }
        Long currentUserId = this.currentRequestUserId(userName);
        if (currentUserId != null) {
            if (YakSecurityContext.hasPermission(permissionCode)) {
                return true;
            }
            return this.permissionExtend.hasPermission(userName, permissionCode);
        }
        User user = this.userService.getUserByUsername(userName);
        if (user != null && user.getId() != null && (snapshot = this.authorizationSnapshotService.get(user.getId())).hasPermission(permissionCode)) {
            return true;
        }
        return this.permissionExtend.hasPermission(userName, permissionCode);
    }

    private Long currentRequestUserId(String userName) {
        Long currentUserId = YakSecurityContext.getCurrentUserId();
        if (currentUserId == null || !Objects.equals(userName, YakSecurityContext.getCurrentUsername())) {
            return null;
        }
        return currentUserId;
    }
}

