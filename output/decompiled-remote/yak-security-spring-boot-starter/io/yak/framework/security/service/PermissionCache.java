/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.service;

import io.yak.framework.security.context.AuthorizationSnapshot;
import java.util.Set;
import java.util.function.Supplier;

public interface PermissionCache {
    public Set<String> get(Long var1, Supplier<Set<String>> var2);

    default public AuthorizationSnapshot getAuthorizationSnapshot(Long userId, Supplier<AuthorizationSnapshot> loader) {
        if (userId == null) {
            return AuthorizationSnapshot.empty();
        }
        AuthorizationSnapshot snapshot = loader.get();
        return snapshot == null ? AuthorizationSnapshot.empty() : snapshot;
    }

    public void invalidateUser(Long var1);

    public void invalidateRole(Long var1);

    public void invalidateAll();
}

