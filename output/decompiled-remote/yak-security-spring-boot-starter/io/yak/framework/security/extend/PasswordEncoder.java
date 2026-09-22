/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.extend;

public interface PasswordEncoder {
    public String encode(CharSequence var1);

    public boolean matches(CharSequence var1, String var2);
}

