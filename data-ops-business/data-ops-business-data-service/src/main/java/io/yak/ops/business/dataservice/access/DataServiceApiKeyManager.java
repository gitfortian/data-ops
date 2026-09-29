package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.domain.access.DataServiceApiKey;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceApiKeyRepository;
import io.yak.ops.business.dataservice.repository.DataServiceRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceApiKeyManager {
  private static final int DEFAULT_RATE_LIMIT = 60;
  private static final int MAX_RATE_LIMIT = 100_000;
  private final DataServiceReader dataServiceReader;
  private final DataServiceRepository dataServiceRepository;
  private final DataServiceApiKeyRepository keyRepository;
  private final ApiKeySecretGenerator secrets;
  private final DataServiceRateLimiter rateLimiter;

  public List<ApiKeyView> listKeys(Long apiId) {
    dataServiceReader.require(apiId);
    return keyRepository.findByApiId(apiId).stream().map(this::view).toList();
  }

  @Transactional
  public AuthMode setAuthMode(Long apiId, String value) {
    DataServiceDefinition definition = dataServiceReader.require(apiId);
    AuthMode mode;
    try { mode = AuthMode.parse(value); }
    catch (IllegalArgumentException exception) { throw new IllegalArgumentException("未知数据服务鉴权模式：" + value, exception); }
    if (mode == AuthMode.API_KEY && validKeys(apiId).isEmpty()) {
      throw new IllegalArgumentException("启用 API Key 鉴权前请至少创建一个有效 Key");
    }
    definition.setAuthMode(mode, LocalDateTime.now());
    dataServiceRepository.save(definition);
    return mode;
  }

  @Transactional
  public CreatedApiKey createKey(Long apiId, ApiKeyInput input) {
    dataServiceReader.require(apiId);
    if (input == null || !StringUtils.hasText(input.name())) throw new IllegalArgumentException("Key 名称不能为空");
    String name = input.name().trim();
    if (name.length() > 128) throw new IllegalArgumentException("Key 名称不能超过 128 个字符");
    int rate = normalizeRate(input.rateLimitPerMinute());
    LocalDateTime expiresAt = normalizeExpiration(input.expiresAt());
    ApiKeySecretGenerator.SecretMaterial secret = secrets.create();
    LocalDateTime now = LocalDateTime.now();
    DataServiceApiKey saved = keyRepository.save(new DataServiceApiKey(
        null, apiId, name, secret.prefix(), secret.hash(), true, rate, expiresAt, null, now, now));
    return new CreatedApiKey(view(saved), secret.rawKey());
  }

  @Transactional
  public CreatedApiKey rotateKey(Long apiId, Long keyId) {
    DataServiceApiKey key = requireKey(apiId, keyId);
    ApiKeySecretGenerator.SecretMaterial secret = secrets.create();
    key.rotate(secret.prefix(), secret.hash(), LocalDateTime.now());
    DataServiceApiKey saved = keyRepository.save(key);
    rateLimiter.invalidate(keyId);
    return new CreatedApiKey(view(saved), secret.rawKey());
  }

  @Transactional
  public ApiKeyView updateKey(Long apiId, Long keyId, ApiKeyUpdate input) {
    DataServiceApiKey key = requireKey(apiId, keyId);
    if (input == null) throw new IllegalArgumentException("Key 配置不能为空");
    String name = null;
    if (StringUtils.hasText(input.name())) {
      name = input.name().trim();
      if (name.length() > 128) throw new IllegalArgumentException("Key 名称不能超过 128 个字符");
    }
    Integer rate = input.rateLimitPerMinute() == null ? null : normalizeRate(input.rateLimitPerMinute());
    LocalDateTime expiresAt = input.expiresAtSet() ? normalizeExpiration(input.expiresAt()) : input.expiresAt();
    key.update(name, rate, expiresAt, input.expiresAtSet(), LocalDateTime.now());
    return view(keyRepository.save(key));
  }

  @Transactional
  public ApiKeyView setKeyEnabled(Long apiId, Long keyId, boolean enabled) {
    DataServiceApiKey key = requireKey(apiId, keyId);
    if (!enabled && key.enabled() && !key.expired(LocalDateTime.now())) ensureAnotherValidKey(apiId, keyId);
    key.setEnabled(enabled, LocalDateTime.now());
    DataServiceApiKey saved = keyRepository.save(key);
    if (!enabled) rateLimiter.invalidate(keyId);
    return view(saved);
  }

  @Transactional
  public void deleteKey(Long apiId, Long keyId) {
    DataServiceApiKey key = requireKey(apiId, keyId);
    if (key.enabled() && !key.expired(LocalDateTime.now())) ensureAnotherValidKey(apiId, keyId);
    keyRepository.delete(keyId); rateLimiter.invalidate(keyId);
  }

  @Transactional
  public void deleteKeysForApi(Long apiId) {
    if (apiId == null) return;
    keyRepository.findByApiId(apiId).forEach(key -> rateLimiter.invalidate(key.id()));
    keyRepository.deleteByApiId(apiId);
  }

  private void ensureAnotherValidKey(Long apiId, Long excluded) {
    if (dataServiceReader.require(apiId).authMode() != AuthMode.API_KEY) return;
    boolean another = validKeys(apiId).stream().anyMatch(key -> !key.id().equals(excluded));
    if (!another) throw new IllegalArgumentException("API Key 鉴权已启用，不能移除最后一个有效 Key");
  }

  private List<DataServiceApiKey> validKeys(Long apiId) {
    LocalDateTime now = LocalDateTime.now();
    return keyRepository.findByApiId(apiId).stream().filter(DataServiceApiKey::enabled).filter(key -> !key.expired(now)).toList();
  }

  private DataServiceApiKey requireKey(Long apiId, Long keyId) {
    dataServiceReader.require(apiId);
    if (keyId == null) throw new IllegalArgumentException("API Key ID 不能为空");
    DataServiceApiKey key = keyRepository.findById(keyId)
        .orElseThrow(() -> new IllegalArgumentException("API Key 不存在：" + keyId));
    if (apiId == null || !apiId.equals(key.apiId())) throw new IllegalArgumentException("API Key 不存在：" + keyId);
    return key;
  }

  private int normalizeRate(Integer value) {
    int result = value == null ? DEFAULT_RATE_LIMIT : value;
    if (result < 1 || result > MAX_RATE_LIMIT) throw new IllegalArgumentException("每分钟调用限制必须在 1~100000 之间");
    return result;
  }

  private LocalDateTime normalizeExpiration(LocalDateTime value) {
    if (value != null && !value.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("API Key 过期时间必须晚于当前时间");
    return value;
  }

  private ApiKeyView view(DataServiceApiKey key) {
    return new ApiKeyView(key.id(), key.apiId(), key.name(), key.keyPrefix(), key.enabled(), key.rateLimitPerMinute(),
        key.expiresAt(), key.lastUsedAt(), key.createTime(), key.updateTime());
  }
}
