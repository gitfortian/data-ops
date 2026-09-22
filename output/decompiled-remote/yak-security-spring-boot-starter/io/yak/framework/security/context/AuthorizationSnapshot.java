/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.context;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AuthorizationSnapshot {
    private static final AuthorizationSnapshot EMPTY = new AuthorizationSnapshot(Collections.emptyList(), Collections.emptySet(), Collections.emptyList(), Collections.emptySet());
    private final List<Long> roleIds;
    private final Set<String> permissionCodes;
    private final List<String> menuCodes;
    private final Set<Long> projectIds;

    public AuthorizationSnapshot(Collection<Long> roleIds, Collection<String> permissionCodes, Collection<String> menuCodes, Collection<Long> projectIds) {
        this.roleIds = AuthorizationSnapshot.immutableIdList(roleIds);
        this.permissionCodes = AuthorizationSnapshot.immutableStringSet(permissionCodes);
        this.menuCodes = AuthorizationSnapshot.immutableStringList(menuCodes);
        this.projectIds = AuthorizationSnapshot.immutableIdSet(projectIds);
    }

    public static AuthorizationSnapshot empty() {
        return EMPTY;
    }

    public static AuthorizationSnapshot forRoleIds(Collection<Long> roleIds) {
        return new AuthorizationSnapshot(roleIds, Collections.emptySet(), Collections.emptyList(), Collections.emptySet());
    }

    public List<Long> getRoleIds() {
        return this.roleIds;
    }

    public Set<String> getPermissionCodes() {
        return this.permissionCodes;
    }

    public List<String> getMenuCodes() {
        return this.menuCodes;
    }

    public Set<Long> getProjectIds() {
        return this.projectIds;
    }

    public boolean hasPermission(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        return this.permissionCodes.contains("security:root") || this.permissionCodes.contains(permissionCode);
    }

    public boolean canAccessProject(Long projectId) {
        return projectId != null && (this.permissionCodes.contains("security:root") || this.projectIds.contains(projectId));
    }

    private static List<Long> immutableIdList(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet normalized = new LinkedHashSet();
        values.stream().filter(Objects::nonNull).filter(value -> value > 0L).forEach(normalized::add);
        return normalized.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList(normalized));
    }

    private static Set<Long> immutableIdSet(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet normalized = new LinkedHashSet();
        values.stream().filter(Objects::nonNull).filter(value -> value > 0L).forEach(normalized::add);
        return normalized.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(normalized);
    }

    private static List<String> immutableStringList(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet normalized = new LinkedHashSet();
        values.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).forEach(normalized::add);
        return normalized.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList(normalized));
    }

    private static Set<String> immutableStringSet(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet normalized = new LinkedHashSet();
        values.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).forEach(normalized::add);
        return normalized.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(normalized);
    }
}

