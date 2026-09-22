/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.servlet.http.HttpServletRequest
 */
package io.yak.framework.security.extend.impl;

import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.framework.security.extend.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;

public class DefaultCurrentUserProvider
implements CurrentUserProvider {
    private final AuthenticationManager authenticationManager;

    public DefaultCurrentUserProvider(AuthenticationManager authenticationManager) {
        this.authenticationManager = Objects.requireNonNull(authenticationManager, "authenticationManager must not be null");
    }

    @Override
    public String getCurrentUser(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return this.authenticationManager.isLogin() ? this.authenticationManager.getLoginUsername() : null;
    }
}

