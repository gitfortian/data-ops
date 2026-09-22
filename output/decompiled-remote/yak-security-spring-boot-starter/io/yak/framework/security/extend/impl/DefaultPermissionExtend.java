/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.extend.impl;

import io.yak.framework.security.extend.PermissionExtend;

public class DefaultPermissionExtend
implements PermissionExtend {
    @Override
    public boolean hasPermission(String user, String permission) {
        return false;
    }
}

