package io.yak.framework.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class YakSecurityPropertiesTest {

  @Test
  void shouldUseMysqlConnectorJByDefault() {
    YakSecurityProperties properties = new YakSecurityProperties();
    assertThat(properties.getDatasource().getDriverClassName())
            .isEqualTo("com.mysql.cj.jdbc.Driver");
  }

  @Test
  void shouldUseSaTokenIdleTimeoutAndMemoryStorageByDefault() {
    YakSecurityProperties properties = new YakSecurityProperties();
    assertThat(properties.getAuthentication().getIdleTimeout())
            .isEqualTo(Duration.ofMinutes(30));
    assertThat(properties.getAuthentication().getStorage())
            .isEqualTo(YakSecurityProperties.AuthenticationStorage.MEMORY);
    assertThat(properties.getAuthentication().getRedis().getHost())
            .isEqualTo("127.0.0.1");
    assertThat(properties.getAuthentication().getRedis().getPort())
            .isEqualTo(6379);
  }
  @Test
  void shouldRetainPropertyPrefixAndAuthenticationDefaults() {
    ConfigurationProperties config = YakSecurityProperties.class.getAnnotation(ConfigurationProperties.class);
    assertThat(config.prefix()).isEqualTo("yak.security");
    YakSecurityProperties properties = new YakSecurityProperties();
    assertThat(properties.getPublicPaths()).contains("/yak-security/api/v1/account/login");
    assertThat(properties.getPermissionCache().isEnabled()).isTrue();
  }

  @Test
  void shouldRejectMissingRequiredDatabaseConfigurationUnlessDisabled() {
    YakSecurityProperties properties = new YakSecurityProperties();
    properties.setApplicationName("unit-test");
    assertThatThrownBy(properties::validateDatabaseConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("yak.security.datasource.url");
    properties.setDatabaseEnabled(false);
    properties.validateDatabaseConfiguration();
  }

  @Test
  void shouldNotLeakPasswordsInPropertyDebugOutput() {
    YakSecurityProperties properties = new YakSecurityProperties();
    properties.getDatasource().setPassword("opaque-db-secret");
    properties.getBootstrap().setPassword("opaque-admin-secret");
    properties.getAuthentication().getRedis().setPassword("opaque-redis-secret");
    assertThat(properties.toString())
            .doesNotContain("opaque-db-secret", "opaque-admin-secret", "opaque-redis-secret");
  }
}
