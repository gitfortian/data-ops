/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.benmanes.caffeine.cache.Cache
 *  com.github.benmanes.caffeine.cache.Caffeine
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
 *  org.springframework.stereotype.Service
 */
package io.yak.framework.security.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.context.AuthorizationSnapshot;
import io.yak.framework.security.dao.UserRoleDao;
import io.yak.framework.security.service.PermissionCache;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(value={PermissionCache.class})
public class CaffeinePermissionCache
implements PermissionCache {
    private final String applicationName;
    private final boolean enabled;
    private final Cache<Key, Set<String>> cache;
    private final Cache<Key, AuthorizationSnapshot> authorizationCache;
    private final UserRoleDao userRoleDao;

    public CaffeinePermissionCache(YakSecurityProperties properties, UserRoleDao userRoleDao) {
        YakSecurityProperties.PermissionCacheProperties settings = properties.getPermissionCache();
        long maximumSize = Math.max(1L, settings.getMaximumSize());
        Duration ttl = Duration.ofMinutes(Math.max(1L, settings.getTtlMinutes()));
        this.applicationName = properties.getApplicationName();
        this.enabled = settings.isEnabled();
        this.userRoleDao = userRoleDao;
        this.cache = Caffeine.newBuilder().maximumSize(maximumSize).expireAfterWrite(ttl).build();
        this.authorizationCache = Caffeine.newBuilder().maximumSize(maximumSize).expireAfterWrite(ttl).build();
    }

    @Override
    public Set<String> get(Long userId, Supplier<Set<String>> loader) {
        if (userId == null) {
            return Collections.emptySet();
        }
        if (!this.enabled) {
            return CaffeinePermissionCache.immutable(loader.get());
        }
        Key key = new Key(this.applicationName, userId);
        return (Set)this.cache.get((Object)key, ignored -> CaffeinePermissionCache.immutable((Set)loader.get()));
    }

    @Override
    public AuthorizationSnapshot getAuthorizationSnapshot(Long userId, Supplier<AuthorizationSnapshot> loader) {
        if (userId == null) {
            return AuthorizationSnapshot.empty();
        }
        if (!this.enabled) {
            return CaffeinePermissionCache.safeSnapshot(loader.get());
        }
        Key key = new Key(this.applicationName, userId);
        return (AuthorizationSnapshot)this.authorizationCache.get((Object)key, ignored -> CaffeinePermissionCache.safeSnapshot((AuthorizationSnapshot)loader.get()));
    }

    @Override
    public void invalidateUser(Long userId) {
        if (userId == null) {
            return;
        }
        Key key = new Key(this.applicationName, userId);
        this.cache.invalidate((Object)key);
        this.authorizationCache.invalidate((Object)key);
    }

    @Override
    public void invalidateRole(Long roleId) {
        if (roleId == null) {
            return;
        }
        this.userRoleDao.selectUserIdListByRoleId(roleId).forEach(this::invalidateUser);
    }

    @Override
    public void invalidateAll() {
        this.cache.invalidateAll();
        this.authorizationCache.invalidateAll();
    }

    private static Set<String> immutable(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<String>(values));
    }

    private static AuthorizationSnapshot safeSnapshot(AuthorizationSnapshot snapshot) {
        return snapshot == null ? AuthorizationSnapshot.empty() : snapshot;
    }

    private static final class Key {
        private final String applicationName;
        private final Long userId;

        private Key(String applicationName, Long userId) {
            this.applicationName = applicationName;
            this.userId = userId;
        }

        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Key)) {
                return false;
            }
            Key other = (Key)object;
            return Objects.equals(this.applicationName, other.applicationName) && Objects.equals(this.userId, other.userId);
        }

        public int hashCode() {
            return Objects.hash(this.applicationName, this.userId);
        }

        public String toString() {
            return "Key{applicationName='" + this.applicationName + "', userId=" + this.userId + "}";
        }
    }
}

