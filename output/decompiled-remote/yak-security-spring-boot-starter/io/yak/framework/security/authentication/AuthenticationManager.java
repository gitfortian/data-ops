/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.authentication;

public interface AuthenticationManager {
    public void login(Long var1);

    default public void login(Long userId, String userName) {
        this.login(userId);
    }

    public void logout();

    default public void logoutUser(Long userId) {
    }

    public boolean isLogin();

    public Long getLoginUserId();

    default public String getLoginUsername() {
        return null;
    }
}

