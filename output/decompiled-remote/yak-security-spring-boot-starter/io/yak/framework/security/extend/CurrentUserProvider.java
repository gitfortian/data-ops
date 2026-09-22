/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.servlet.http.HttpServletRequest
 */
package io.yak.framework.security.extend;

import jakarta.servlet.http.HttpServletRequest;

@FunctionalInterface
public interface CurrentUserProvider {
    public String getCurrentUser(HttpServletRequest var1);
}

