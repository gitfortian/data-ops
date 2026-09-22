/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.extend.impl;

import io.yak.framework.security.extend.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;

public class DefaultPasswordEncoder
implements PasswordEncoder {
    private static final int DEFAULT_STRENGTH = 10;
    private final BCryptPasswordEncoder delegate;

    public DefaultPasswordEncoder() {
        this(10);
    }

    public DefaultPasswordEncoder(int strength) {
        this.delegate = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null || !StringUtils.hasText((String)rawPassword.toString())) {
            throw new IllegalArgumentException("rawPassword must not be blank");
        }
        return this.delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || !StringUtils.hasText((String)encodedPassword)) {
            return false;
        }
        try {
            return this.delegate.matches(rawPassword, encodedPassword);
        }
        catch (IllegalArgumentException exception) {
            return false;
        }
    }
}

