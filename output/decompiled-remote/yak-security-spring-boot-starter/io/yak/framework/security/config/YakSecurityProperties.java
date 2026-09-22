/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  org.springframework.boot.context.properties.ConfigurationProperties
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Generated;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix="yak.security")
public class YakSecurityProperties {
    public static final String PREFIX = "yak.security";
    private boolean enabled = true;
    private boolean databaseEnabled = true;
    private boolean webEnabled = true;
    private boolean authenticationEnabled = true;
    private List<String> publicPaths = new ArrayList<String>(Arrays.asList("/yak-security/api/v1/account/login", "/yak-security/api/v1/common/heart", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"));
    private boolean auditEnabled = true;
    private String applicationName;
    private final DataSourceProperties datasource = new DataSourceProperties();
    private final AuthenticationProperties authentication = new AuthenticationProperties();
    private final BootstrapProperties bootstrap = new BootstrapProperties();
    private final PermissionRegistrationProperties permissionRegistration = new PermissionRegistrationProperties();
    private final PermissionCacheProperties permissionCache = new PermissionCacheProperties();
    private final LoginSecurityProperties login = new LoginSecurityProperties();

    public void validateDatabaseConfiguration() {
        if (!(this.enabled && this.databaseEnabled && this.datasource.isEnabled())) {
            return;
        }
        YakSecurityProperties.requireText(this.applicationName, "yak.security.application-name");
        this.datasource.validate();
    }

    private static void requireText(String value, String key) {
        if (!StringUtils.hasText((String)value)) {
            throw new IllegalStateException("Missing required configuration: " + key);
        }
    }

    private static IllegalStateException invalidProperty(String key, String message) {
        return new IllegalStateException("Invalid configuration: " + key + " " + message);
    }

    @Generated
    public boolean isEnabled() {
        return this.enabled;
    }

    @Generated
    public boolean isDatabaseEnabled() {
        return this.databaseEnabled;
    }

    @Generated
    public boolean isWebEnabled() {
        return this.webEnabled;
    }

    @Generated
    public boolean isAuthenticationEnabled() {
        return this.authenticationEnabled;
    }

    @Generated
    public List<String> getPublicPaths() {
        return this.publicPaths;
    }

    @Generated
    public boolean isAuditEnabled() {
        return this.auditEnabled;
    }

    @Generated
    public String getApplicationName() {
        return this.applicationName;
    }

    @Generated
    public DataSourceProperties getDatasource() {
        return this.datasource;
    }

    @Generated
    public AuthenticationProperties getAuthentication() {
        return this.authentication;
    }

    @Generated
    public BootstrapProperties getBootstrap() {
        return this.bootstrap;
    }

    @Generated
    public PermissionRegistrationProperties getPermissionRegistration() {
        return this.permissionRegistration;
    }

    @Generated
    public PermissionCacheProperties getPermissionCache() {
        return this.permissionCache;
    }

    @Generated
    public LoginSecurityProperties getLogin() {
        return this.login;
    }

    @Generated
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Generated
    public void setDatabaseEnabled(boolean databaseEnabled) {
        this.databaseEnabled = databaseEnabled;
    }

    @Generated
    public void setWebEnabled(boolean webEnabled) {
        this.webEnabled = webEnabled;
    }

    @Generated
    public void setAuthenticationEnabled(boolean authenticationEnabled) {
        this.authenticationEnabled = authenticationEnabled;
    }

    @Generated
    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    @Generated
    public void setAuditEnabled(boolean auditEnabled) {
        this.auditEnabled = auditEnabled;
    }

    @Generated
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    @Generated
    public String toString() {
        return "YakSecurityProperties(enabled=" + this.isEnabled() + ", databaseEnabled=" + this.isDatabaseEnabled() + ", webEnabled=" + this.isWebEnabled() + ", authenticationEnabled=" + this.isAuthenticationEnabled() + ", publicPaths=" + String.valueOf(this.getPublicPaths()) + ", auditEnabled=" + this.isAuditEnabled() + ", applicationName=" + this.getApplicationName() + ", datasource=" + String.valueOf(this.getDatasource()) + ", authentication=" + String.valueOf(this.getAuthentication()) + ", bootstrap=" + String.valueOf(this.getBootstrap()) + ", permissionRegistration=" + String.valueOf(this.getPermissionRegistration()) + ", permissionCache=" + String.valueOf(this.getPermissionCache()) + ", login=" + String.valueOf(this.getLogin()) + ")";
    }

    public static class DataSourceProperties {
        private boolean enabled = true;
        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private int initialSize = 1;
        private int minIdle = 1;
        private int maxActive = 8;
        private long maxWait = 60000L;
        private String validationQuery = "SELECT 1";
        private boolean testWhileIdle = true;
        private boolean testOnBorrow = false;
        private boolean testOnReturn = false;

        private void validate() {
            boolean connectionValidationEnabled;
            YakSecurityProperties.requireText(this.url, "yak.security.datasource.url");
            YakSecurityProperties.requireText(this.username, "yak.security.datasource.username");
            YakSecurityProperties.requireText(this.driverClassName, "yak.security.datasource.driver-class-name");
            if (this.initialSize < 0) {
                throw YakSecurityProperties.invalidProperty("yak.security.datasource.initial-size", "must be greater than or equal to 0");
            }
            if (this.minIdle < 0) {
                throw YakSecurityProperties.invalidProperty("yak.security.datasource.min-idle", "must be greater than or equal to 0");
            }
            if (this.maxActive <= 0) {
                throw YakSecurityProperties.invalidProperty("yak.security.datasource.max-active", "must be greater than 0");
            }
            if (this.initialSize > this.maxActive) {
                throw YakSecurityProperties.invalidProperty("yak.security.datasource.initial-size", "must not be greater than max-active");
            }
            if (this.minIdle > this.maxActive) {
                throw YakSecurityProperties.invalidProperty("yak.security.datasource.min-idle", "must not be greater than max-active");
            }
            if (this.maxWait < -1L) {
                throw YakSecurityProperties.invalidProperty("yak.security.datasource.max-wait", "must be -1 or greater than or equal to 0");
            }
            boolean bl = connectionValidationEnabled = this.testWhileIdle || this.testOnBorrow || this.testOnReturn;
            if (connectionValidationEnabled && !StringUtils.hasText((String)this.validationQuery)) {
                throw YakSecurityProperties.invalidProperty("yak.security.datasource.validation-query", "must not be blank when connection validation is enabled");
            }
        }

        @Generated
        public boolean isEnabled() {
            return this.enabled;
        }

        @Generated
        public String getUrl() {
            return this.url;
        }

        @Generated
        public String getUsername() {
            return this.username;
        }

        @Generated
        public String getPassword() {
            return this.password;
        }

        @Generated
        public String getDriverClassName() {
            return this.driverClassName;
        }

        @Generated
        public int getInitialSize() {
            return this.initialSize;
        }

        @Generated
        public int getMinIdle() {
            return this.minIdle;
        }

        @Generated
        public int getMaxActive() {
            return this.maxActive;
        }

        @Generated
        public long getMaxWait() {
            return this.maxWait;
        }

        @Generated
        public String getValidationQuery() {
            return this.validationQuery;
        }

        @Generated
        public boolean isTestWhileIdle() {
            return this.testWhileIdle;
        }

        @Generated
        public boolean isTestOnBorrow() {
            return this.testOnBorrow;
        }

        @Generated
        public boolean isTestOnReturn() {
            return this.testOnReturn;
        }

        @Generated
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Generated
        public void setUrl(String url) {
            this.url = url;
        }

        @Generated
        public void setUsername(String username) {
            this.username = username;
        }

        @Generated
        public void setPassword(String password) {
            this.password = password;
        }

        @Generated
        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        @Generated
        public void setInitialSize(int initialSize) {
            this.initialSize = initialSize;
        }

        @Generated
        public void setMinIdle(int minIdle) {
            this.minIdle = minIdle;
        }

        @Generated
        public void setMaxActive(int maxActive) {
            this.maxActive = maxActive;
        }

        @Generated
        public void setMaxWait(long maxWait) {
            this.maxWait = maxWait;
        }

        @Generated
        public void setValidationQuery(String validationQuery) {
            this.validationQuery = validationQuery;
        }

        @Generated
        public void setTestWhileIdle(boolean testWhileIdle) {
            this.testWhileIdle = testWhileIdle;
        }

        @Generated
        public void setTestOnBorrow(boolean testOnBorrow) {
            this.testOnBorrow = testOnBorrow;
        }

        @Generated
        public void setTestOnReturn(boolean testOnReturn) {
            this.testOnReturn = testOnReturn;
        }

        @Generated
        public String toString() {
            return "YakSecurityProperties.DataSourceProperties(enabled=" + this.isEnabled() + ", url=" + this.getUrl() + ", username=" + this.getUsername() + ", driverClassName=" + this.getDriverClassName() + ", initialSize=" + this.getInitialSize() + ", minIdle=" + this.getMinIdle() + ", maxActive=" + this.getMaxActive() + ", maxWait=" + this.getMaxWait() + ", validationQuery=" + this.getValidationQuery() + ", testWhileIdle=" + this.isTestWhileIdle() + ", testOnBorrow=" + this.isTestOnBorrow() + ", testOnReturn=" + this.isTestOnReturn() + ")";
        }
    }

    public static class AuthenticationProperties {
        private Duration idleTimeout = Duration.ofMinutes(30L);
        private AuthenticationStorage storage = AuthenticationStorage.MEMORY;
        private final RedisStorageProperties redis = new RedisStorageProperties();

        @Generated
        public Duration getIdleTimeout() {
            return this.idleTimeout;
        }

        @Generated
        public AuthenticationStorage getStorage() {
            return this.storage;
        }

        @Generated
        public RedisStorageProperties getRedis() {
            return this.redis;
        }

        @Generated
        public void setIdleTimeout(Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        @Generated
        public void setStorage(AuthenticationStorage storage) {
            this.storage = storage;
        }

        @Generated
        public String toString() {
            return "YakSecurityProperties.AuthenticationProperties(idleTimeout=" + String.valueOf(this.getIdleTimeout()) + ", storage=" + String.valueOf((Object)this.getStorage()) + ", redis=" + String.valueOf(this.getRedis()) + ")";
        }
    }

    public static class BootstrapProperties {
        private boolean enabled = false;
        private String username = "admin";
        private String password;
        private String realName = "\u7cfb\u7edf\u7ba1\u7406\u5458";

        @Generated
        public boolean isEnabled() {
            return this.enabled;
        }

        @Generated
        public String getUsername() {
            return this.username;
        }

        @Generated
        public String getPassword() {
            return this.password;
        }

        @Generated
        public String getRealName() {
            return this.realName;
        }

        @Generated
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Generated
        public void setUsername(String username) {
            this.username = username;
        }

        @Generated
        public void setPassword(String password) {
            this.password = password;
        }

        @Generated
        public void setRealName(String realName) {
            this.realName = realName;
        }

        @Generated
        public String toString() {
            return "YakSecurityProperties.BootstrapProperties(enabled=" + this.isEnabled() + ", username=" + this.getUsername() + ", realName=" + this.getRealName() + ")";
        }
    }

    public static class PermissionRegistrationProperties {
        private boolean enabled = true;

        @Generated
        public boolean isEnabled() {
            return this.enabled;
        }

        @Generated
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Generated
        public String toString() {
            return "YakSecurityProperties.PermissionRegistrationProperties(enabled=" + this.isEnabled() + ")";
        }
    }

    public static class PermissionCacheProperties {
        private boolean enabled = true;
        private long ttlMinutes = 20L;
        private long maximumSize = 10000L;

        @Generated
        public boolean isEnabled() {
            return this.enabled;
        }

        @Generated
        public long getTtlMinutes() {
            return this.ttlMinutes;
        }

        @Generated
        public long getMaximumSize() {
            return this.maximumSize;
        }

        @Generated
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Generated
        public void setTtlMinutes(long ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }

        @Generated
        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        @Generated
        public String toString() {
            return "YakSecurityProperties.PermissionCacheProperties(enabled=" + this.isEnabled() + ", ttlMinutes=" + this.getTtlMinutes() + ", maximumSize=" + this.getMaximumSize() + ")";
        }
    }

    public static class LoginSecurityProperties {
        private int maxFailureCount = 5;
        private Duration lockDuration = Duration.ofMinutes(15L);
        private boolean hideAccountNotFound = true;

        @Generated
        public int getMaxFailureCount() {
            return this.maxFailureCount;
        }

        @Generated
        public Duration getLockDuration() {
            return this.lockDuration;
        }

        @Generated
        public boolean isHideAccountNotFound() {
            return this.hideAccountNotFound;
        }

        @Generated
        public void setMaxFailureCount(int maxFailureCount) {
            this.maxFailureCount = maxFailureCount;
        }

        @Generated
        public void setLockDuration(Duration lockDuration) {
            this.lockDuration = lockDuration;
        }

        @Generated
        public void setHideAccountNotFound(boolean hideAccountNotFound) {
            this.hideAccountNotFound = hideAccountNotFound;
        }

        @Generated
        public String toString() {
            return "YakSecurityProperties.LoginSecurityProperties(maxFailureCount=" + this.getMaxFailureCount() + ", lockDuration=" + String.valueOf(this.getLockDuration()) + ", hideAccountNotFound=" + this.isHideAccountNotFound() + ")";
        }
    }

    public static class RedisStorageProperties {
        private String host = "127.0.0.1";
        private int port = 6379;
        private String password;
        private int database = 0;
        private int maxTotal = 64;

        @Generated
        public String getHost() {
            return this.host;
        }

        @Generated
        public int getPort() {
            return this.port;
        }

        @Generated
        public String getPassword() {
            return this.password;
        }

        @Generated
        public int getDatabase() {
            return this.database;
        }

        @Generated
        public int getMaxTotal() {
            return this.maxTotal;
        }

        @Generated
        public void setHost(String host) {
            this.host = host;
        }

        @Generated
        public void setPort(int port) {
            this.port = port;
        }

        @Generated
        public void setPassword(String password) {
            this.password = password;
        }

        @Generated
        public void setDatabase(int database) {
            this.database = database;
        }

        @Generated
        public void setMaxTotal(int maxTotal) {
            this.maxTotal = maxTotal;
        }

        @Generated
        public String toString() {
            return "YakSecurityProperties.RedisStorageProperties(host=" + this.getHost() + ", port=" + this.getPort() + ", database=" + this.getDatabase() + ", maxTotal=" + this.getMaxTotal() + ")";
        }
    }

    public static enum AuthenticationStorage {
        MEMORY,
        REDIS;

    }
}

