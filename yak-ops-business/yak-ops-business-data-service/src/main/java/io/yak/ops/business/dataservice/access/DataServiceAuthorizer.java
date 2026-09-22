package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.access.AccessContext;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.domain.access.DataServiceApiKey;
import io.yak.ops.business.dataservice.domain.access.DataServiceConsumer;
import io.yak.ops.business.dataservice.repository.DataServiceApiKeyRepository;
import io.yak.ops.business.dataservice.repository.DataServiceConsumerRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceAuthorizer {
  private final DataServiceApiKeyRepository repository;
  private final ApiKeySecretGenerator secrets;
  private final DataServiceRateLimiter rateLimiter;
  private final DataServiceIpAccessAuthorizer ipAccessAuthorizer;
  private final DataServiceConsumerRepository consumerRepository;
  private final DataServiceConsumerIpAccessAuthorizer consumerIpAccessAuthorizer;

  public AccessContext authorize(DataServiceDefinition definition, String rawKey) {
    return authorize(definition, rawKey, null);
  }

  /** API hard-gate IP policy runs first; consumer identity policy runs after credential resolution. */
  public AccessContext authorize(DataServiceDefinition definition, String rawKey, String clientIp) {
    ipAccessAuthorizer.authorize(definition.id(), clientIp);

    // Presence of a consumer grant is the primary access model. Disabled consumers still keep the
    // API protected (fail closed); they simply cannot authenticate.
    if (consumerRepository.hasConfiguredAccess(definition.projectId(), definition.id())) {
      return authorizeConsumer(definition, rawKey, clientIp);
    }

    // Compatibility corridor for APIs that have not been moved into the consumer model yet.
    if (definition.authMode() == AuthMode.NONE) return AccessContext.publicAccess();
    return authorizeLegacy(definition, rawKey);
  }

  private AccessContext authorizeConsumer(
      DataServiceDefinition definition, String rawKey, String clientIp) {
    if (rawKey == null || rawKey.isBlank()) {
      throw new DataServiceUnauthorizedException("缺少 X-API-Key 请求头");
    }
    DataServiceApiKey key = repository.findByHash(secrets.hash(rawKey.trim()))
        .orElseThrow(this::invalidConsumerKey);

    // A legacy API-scoped key created through the compatibility endpoints remains valid for its
    // own API even after consumer management has been enabled for the same service.
    if (key.consumerId() == null) {
      if (definition.id().equals(key.apiId())) return admitLegacyKey(key);
      throw invalidConsumerKey();
    }

    DataServiceConsumer consumer = consumerRepository
        .findByIdForProject(key.consumerId(), definition.projectId())
        .orElseThrow(this::invalidConsumerKey);
    if (!consumer.enabled()) throw invalidConsumerKey();
    if (!consumerRepository.hasAccess(consumer.id(), definition.projectId(), definition.id())) {
      throw invalidConsumerKey();
    }

    LocalDateTime now = LocalDateTime.now();
    validateKey(key, now);
    consumerIpAccessAuthorizer.authorize(consumer.id(), clientIp);
    admit(key, now);
    // Keep the existing audit schema: apiKeyName now records the stable caller name.
    return new AccessContext("API_KEY", key.id(), consumer.name(), key.keyPrefix());
  }

  private AccessContext authorizeLegacy(DataServiceDefinition definition, String rawKey) {
    if (rawKey == null || rawKey.isBlank()) {
      throw new DataServiceUnauthorizedException("缺少 X-API-Key 请求头");
    }
    DataServiceApiKey key = repository.findByHash(definition.id(), secrets.hash(rawKey.trim()))
        .orElseThrow(() -> new DataServiceUnauthorizedException("API Key 无效或已停用"));
    return admitLegacyKey(key);
  }

  private AccessContext admitLegacyKey(DataServiceApiKey key) {
    LocalDateTime now = LocalDateTime.now();
    validateKey(key, now);
    admit(key, now);
    return new AccessContext("API_KEY", key.id(), key.name(), key.keyPrefix());
  }

  private void validateKey(DataServiceApiKey key, LocalDateTime now) {
    if (!key.enabled()) throw new DataServiceUnauthorizedException("API Key 无效或已停用");
    if (key.expired(now)) throw new DataServiceUnauthorizedException("API Key 已过期");
  }

  private void admit(DataServiceApiKey key, LocalDateTime now) {
    rateLimiter.acquire(key);
    key.markUsed(now);
    repository.save(key);
  }

  private DataServiceUnauthorizedException invalidConsumerKey() {
    return new DataServiceUnauthorizedException("API Key 无效或无权访问当前 API");
  }
}
