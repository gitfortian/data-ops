/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.extend.impl;

import io.yak.framework.security.config.YakSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LoginAttemptGuard {
    private final ConcurrentMap<String, FailureState> failures = new ConcurrentHashMap<String, FailureState>();
    private final int maximumFailures;
    private final Duration lockDuration;
    private final Clock clock;

    public LoginAttemptGuard(YakSecurityProperties.LoginSecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginAttemptGuard(YakSecurityProperties.LoginSecurityProperties properties, Clock clock) {
        if (properties == null) {
            throw new IllegalArgumentException("login security properties must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (properties.getMaxFailureCount() < 1) {
            throw new IllegalArgumentException("max-failure-count must be greater than 0");
        }
        if (properties.getLockDuration() == null || properties.getLockDuration().isNegative() || properties.getLockDuration().isZero()) {
            throw new IllegalArgumentException("lock-duration must be greater than 0");
        }
        this.maximumFailures = properties.getMaxFailureCount();
        this.lockDuration = properties.getLockDuration();
        this.clock = clock;
    }

    public boolean isBlocked(String username, String ipAddress) {
        Instant now = this.clock.instant();
        return this.isBlocked(LoginAttemptGuard.key("user", LoginAttemptGuard.normalizeUsername(username)), now) || this.isBlocked(LoginAttemptGuard.key("ip", LoginAttemptGuard.normalizeIp(ipAddress)), now);
    }

    public void recordFailure(String username, String ipAddress) {
        Instant now = this.clock.instant();
        this.recordFailure(LoginAttemptGuard.key("user", LoginAttemptGuard.normalizeUsername(username)), now);
        this.recordFailure(LoginAttemptGuard.key("ip", LoginAttemptGuard.normalizeIp(ipAddress)), now);
    }

    public void recordSuccess(String username, String ipAddress) {
        this.failures.remove(LoginAttemptGuard.key("user", LoginAttemptGuard.normalizeUsername(username)));
        this.failures.remove(LoginAttemptGuard.key("ip", LoginAttemptGuard.normalizeIp(ipAddress)));
    }

    private boolean isBlocked(String key, Instant now) {
        FailureState state = (FailureState)this.failures.get(key);
        if (state == null || state.blockedUntil == null) {
            return false;
        }
        if (!now.isBefore(state.blockedUntil)) {
            this.failures.remove(key, state);
            return false;
        }
        return true;
    }

    private void recordFailure(String key, Instant now) {
        this.failures.compute(key, (ignored, current) -> {
            int count;
            if (current == null || LoginAttemptGuard.isLockExpired(current, now)) {
                current = new FailureState(0, null);
            }
            Instant blockedUntil = (count = current.count + 1) >= this.maximumFailures ? now.plus(this.lockDuration) : null;
            return new FailureState(count, blockedUntil);
        });
    }

    private static boolean isLockExpired(FailureState state, Instant now) {
        return state.blockedUntil != null && !now.isBefore(state.blockedUntil);
    }

    private static String key(String dimension, String value) {
        return dimension + ":" + value;
    }

    private static String normalizeUsername(String username) {
        return username == null ? "<blank>" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeIp(String ipAddress) {
        return ipAddress == null || ipAddress.trim().isEmpty() ? "<unknown>" : ipAddress.trim();
    }

    private static final class FailureState {
        private final int count;
        private final Instant blockedUntil;

        private FailureState(int count, Instant blockedUntil) {
            this.count = count;
            this.blockedUntil = blockedUntil;
        }
    }
}

