/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.context;

import io.yak.framework.security.context.AuthorizationSnapshot;
import io.yak.framework.security.context.CurrentUser;
import java.util.List;
import java.util.Set;

public final class YakSecurityContext {
    private static final CurrentUser ANONYMOUS = new ImmutableCurrentUser(null, null, null, AuthorizationSnapshot.empty(), false);
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal();

    private YakSecurityContext() {
        throw new IllegalStateException("Utility class");
    }

    public static Long getCurrentUserId() {
        return YakSecurityContext.currentUser().getUserId();
    }

    public static String getCurrentUsername() {
        return YakSecurityContext.currentUser().getUsername();
    }

    public static Long getCurrentProjectId() {
        return YakSecurityContext.currentUser().getProjectId();
    }

    public static List<Long> getCurrentRoleIds() {
        return YakSecurityContext.currentUser().getRoleIds();
    }

    public static Set<String> getCurrentPermissionCodes() {
        return YakSecurityContext.currentUser().getPermissionCodes();
    }

    public static List<String> getCurrentMenuCodes() {
        return YakSecurityContext.currentUser().getMenuCodes();
    }

    public static Set<Long> getCurrentProjectIds() {
        return YakSecurityContext.currentUser().getProjectIds();
    }

    public static boolean hasPermission(String permissionCode) {
        return YakSecurityContext.currentUser().hasPermission(permissionCode);
    }

    public static boolean canAccessProject(Long projectId) {
        return YakSecurityContext.currentUser().canAccessProject(projectId);
    }

    public static boolean isAuthenticated() {
        return YakSecurityContext.currentUser().isAuthenticated();
    }

    static CurrentUser currentUser() {
        CurrentUser currentUser = HOLDER.get();
        return currentUser == null ? ANONYMOUS : currentUser;
    }

    static void setCurrentUser(CurrentUser currentUser) {
        HOLDER.set(currentUser);
    }

    static void clear() {
        HOLDER.remove();
    }

    static final class ImmutableCurrentUser
    implements CurrentUser {
        private final Long userId;
        private final String username;
        private final Long projectId;
        private final AuthorizationSnapshot authorizationSnapshot;
        private final boolean authenticated;

        ImmutableCurrentUser(Long userId, String username, Long projectId, List<Long> roleIds, boolean authenticated) {
            this(userId, username, projectId, AuthorizationSnapshot.forRoleIds(roleIds), authenticated);
        }

        ImmutableCurrentUser(Long userId, String username, Long projectId, AuthorizationSnapshot authorizationSnapshot, boolean authenticated) {
            this.userId = userId;
            this.username = username;
            this.projectId = projectId;
            this.authorizationSnapshot = authorizationSnapshot == null ? AuthorizationSnapshot.empty() : authorizationSnapshot;
            this.authenticated = authenticated;
        }

        @Override
        public Long getUserId() {
            return this.userId;
        }

        @Override
        public String getUsername() {
            return this.username;
        }

        @Override
        public Long getProjectId() {
            return this.projectId;
        }

        @Override
        public List<Long> getRoleIds() {
            return this.authorizationSnapshot.getRoleIds();
        }

        @Override
        public Set<String> getPermissionCodes() {
            return this.authorizationSnapshot.getPermissionCodes();
        }

        @Override
        public List<String> getMenuCodes() {
            return this.authorizationSnapshot.getMenuCodes();
        }

        @Override
        public Set<Long> getProjectIds() {
            return this.authorizationSnapshot.getProjectIds();
        }

        @Override
        public boolean hasPermission(String permissionCode) {
            return this.authorizationSnapshot.hasPermission(permissionCode);
        }

        @Override
        public boolean canAccessProject(Long projectId) {
            return this.authorizationSnapshot.canAccessProject(projectId);
        }

        @Override
        public boolean isAuthenticated() {
            return this.authenticated;
        }
    }
}

