/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.servlet.FilterChain
 *  jakarta.servlet.ServletException
 *  jakarta.servlet.ServletRequest
 *  jakarta.servlet.ServletResponse
 *  jakarta.servlet.http.HttpServletRequest
 *  jakarta.servlet.http.HttpServletResponse
 *  org.springframework.beans.factory.ObjectProvider
 *  org.springframework.util.StringUtils
 *  org.springframework.web.filter.OncePerRequestFilter
 */
package io.yak.framework.security.context;

import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.framework.security.context.AuthorizationSnapshot;
import io.yak.framework.security.context.CurrentUser;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.framework.security.dao.UserRoleDao;
import io.yak.framework.security.service.impl.AuthorizationSnapshotService;
import io.yak.framework.security.util.HttpRequestUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public final class YakSecurityContextFilter
extends OncePerRequestFilter {
    private final ObjectProvider<UserRoleDao> userRoleDaoProvider;
    private final ObjectProvider<AuthenticationManager> authenticationManagerProvider;
    private final ObjectProvider<AuthorizationSnapshotService> authorizationSnapshotServiceProvider;

    public YakSecurityContextFilter(ObjectProvider<UserRoleDao> userRoleDaoProvider, ObjectProvider<AuthenticationManager> authenticationManagerProvider) {
        this(userRoleDaoProvider, authenticationManagerProvider, null);
    }

    public YakSecurityContextFilter(ObjectProvider<UserRoleDao> userRoleDaoProvider, ObjectProvider<AuthenticationManager> authenticationManagerProvider, ObjectProvider<AuthorizationSnapshotService> authorizationSnapshotServiceProvider) {
        this.userRoleDaoProvider = userRoleDaoProvider;
        this.authenticationManagerProvider = authenticationManagerProvider;
        this.authorizationSnapshotServiceProvider = authorizationSnapshotServiceProvider;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        YakSecurityContext.setCurrentUser(this.resolve(request));
        try {
            filterChain.doFilter((ServletRequest)request, (ServletResponse)response);
        }
        finally {
            YakSecurityContext.clear();
        }
    }

    private CurrentUser resolve(HttpServletRequest request) {
        AuthenticationManager authenticationManager;
        AuthenticationManager authenticationManager2 = authenticationManager = this.authenticationManagerProvider == null ? null : (AuthenticationManager)this.authenticationManagerProvider.getIfAvailable();
        if (authenticationManager == null || !authenticationManager.isLogin()) {
            return this.buildCurrentUser(request, null, null);
        }
        return this.buildCurrentUser(request, authenticationManager.getLoginUserId(), authenticationManager.getLoginUsername());
    }

    private CurrentUser buildCurrentUser(HttpServletRequest request, Long userId, String username) {
        boolean authenticated = userId != null && StringUtils.hasText((String)username);
        AuthorizationSnapshot snapshot = authenticated ? this.findAuthorizationSnapshot(userId) : AuthorizationSnapshot.empty();
        return new YakSecurityContext.ImmutableCurrentUser(userId, username, HttpRequestUtil.getProjectId(request), snapshot, authenticated);
    }

    private AuthorizationSnapshot findAuthorizationSnapshot(Long userId) {
        AuthorizationSnapshotService snapshotService;
        AuthorizationSnapshotService authorizationSnapshotService = snapshotService = this.authorizationSnapshotServiceProvider == null ? null : (AuthorizationSnapshotService)this.authorizationSnapshotServiceProvider.getIfAvailable();
        if (snapshotService != null) {
            return snapshotService.get(userId);
        }
        return AuthorizationSnapshot.forRoleIds(this.findRoleIds(userId));
    }

    private List<Long> findRoleIds(Long userId) {
        UserRoleDao userRoleDao;
        UserRoleDao userRoleDao2 = userRoleDao = this.userRoleDaoProvider == null ? null : (UserRoleDao)this.userRoleDaoProvider.getIfAvailable();
        if (userRoleDao == null) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoleDao.selectRoleIdListByUserId(userId);
        return roleIds == null ? Collections.emptyList() : roleIds;
    }
}

