/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.context;

import io.yak.framework.security.context.AuthorizationSnapshot;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface CurrentUser {
    public Long getUserId();

    public String getUsername();

    public Long getProjectId();

    public List<Long> getRoleIds();

    default public Set<String> getPermissionCodes() {
        return Collections.emptySet();
    }

    default public List<String> getMenuCodes() {
        return Collections.emptyList();
    }

    default public Set<Long> getProjectIds() {
        return Collections.emptySet();
    }

    default public boolean hasPermission(String permissionCode) {
        return new AuthorizationSnapshot(this.getRoleIds(), this.getPermissionCodes(), this.getMenuCodes(), this.getProjectIds()).hasPermission(permissionCode);
    }

    default public boolean canAccessProject(Long projectId) {
        return new AuthorizationSnapshot(this.getRoleIds(), this.getPermissionCodes(), this.getMenuCodes(), this.getProjectIds()).canAccessProject(projectId);
    }

    public boolean isAuthenticated();
}

