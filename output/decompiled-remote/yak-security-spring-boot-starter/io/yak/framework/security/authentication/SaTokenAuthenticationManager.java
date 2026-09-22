/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  cn.dev33.satoken.config.SaTokenConfig
 *  cn.dev33.satoken.session.SaSession
 *  cn.dev33.satoken.stp.StpLogic
 *  cn.dev33.satoken.stp.parameter.SaLoginParameter
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.authentication;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import io.yak.framework.security.authentication.AuthenticationManager;
import java.time.Duration;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class SaTokenAuthenticationManager
implements AuthenticationManager {
    private static final String USERNAME_KEY = "yak-security:username";
    private final StpLogic stpLogic;
    private final Long activeTimeoutSeconds;

    public SaTokenAuthenticationManager(StpLogic stpLogic) {
        this(stpLogic, null);
    }

    public SaTokenAuthenticationManager(StpLogic stpLogic, Duration activeTimeout) {
        this.stpLogic = Objects.requireNonNull(stpLogic, "stpLogic must not be null");
        if (activeTimeout == null) {
            this.activeTimeoutSeconds = null;
            return;
        }
        long seconds = activeTimeout.getSeconds();
        if (seconds < 1L) {
            throw new IllegalArgumentException("activeTimeout must be greater than 0 seconds");
        }
        this.activeTimeoutSeconds = seconds;
        SaTokenConfig config = this.stpLogic.getConfigOrGlobal();
        config.setDynamicActiveTimeout(Boolean.valueOf(true));
    }

    @Override
    public void login(Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (this.activeTimeoutSeconds == null) {
            this.stpLogic.login((Object)userId);
            return;
        }
        SaLoginParameter loginParameter = this.stpLogic.createSaLoginParameter().setActiveTimeout(this.activeTimeoutSeconds.longValue()).setIsLastingCookie(Boolean.valueOf(false));
        this.stpLogic.login((Object)userId, loginParameter);
    }

    @Override
    public void login(Long userId, String userName) {
        if (!StringUtils.hasText((String)userName)) {
            throw new IllegalArgumentException("userName must not be blank");
        }
        this.login(userId);
        SaSession tokenSession = this.stpLogic.getTokenSession(true);
        tokenSession.set(USERNAME_KEY, (Object)userName);
    }

    @Override
    public void logout() {
        this.stpLogic.logout();
    }

    @Override
    public void logoutUser(Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        this.stpLogic.logout((Object)userId);
    }

    @Override
    public boolean isLogin() {
        return this.stpLogic.isLogin();
    }

    @Override
    public Long getLoginUserId() {
        Object loginId = this.stpLogic.getLoginIdDefaultNull();
        if (loginId == null) {
            return null;
        }
        if (loginId instanceof Number) {
            Number number = (Number)loginId;
            return number.longValue();
        }
        if (loginId instanceof CharSequence) {
            CharSequence sequence = (CharSequence)loginId;
            String value = sequence.toString().trim();
            if (value.isEmpty()) {
                throw this.invalidLoginId(loginId, null);
            }
            try {
                return Long.valueOf(value);
            }
            catch (NumberFormatException exception) {
                throw this.invalidLoginId(loginId, exception);
            }
        }
        throw this.invalidLoginId(loginId, null);
    }

    @Override
    public String getLoginUsername() {
        String text;
        if (!this.stpLogic.isLogin()) {
            return null;
        }
        SaSession tokenSession = this.stpLogic.getTokenSession(false);
        if (tokenSession == null) {
            return null;
        }
        Object value = tokenSession.get(USERNAME_KEY);
        return value instanceof String && StringUtils.hasText((String)(text = (String)value)) ? text : null;
    }

    private IllegalStateException invalidLoginId(Object loginId, Exception cause) {
        String message = "Sa-Token loginId must be convertible to Long, actual type=" + loginId.getClass().getName();
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }
}

