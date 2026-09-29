package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.domain.access.ConsumerAccessScope;
import io.yak.ops.business.dataservice.domain.access.DataServiceApiKey;
import io.yak.ops.business.dataservice.domain.access.DataServiceConsumer;
import io.yak.ops.business.dataservice.domain.access.IpAccessMode;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceApiKeyRepository;
import io.yak.ops.business.dataservice.repository.DataServiceConsumerIpAccessRepository;
import io.yak.ops.business.dataservice.repository.DataServiceConsumerRepository;
import io.yak.ops.business.dataservice.repository.DataServiceRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceConsumerManager {
  private static final int DEFAULT_RATE_LIMIT = 60;
  private static final int MAX_RATE_LIMIT = 100_000;

  private final DataServiceConsumerRepository consumerRepository;
  private final DataServiceConsumerIpAccessRepository ipAccessRepository;
  private final DataServiceApiKeyRepository keyRepository;
  private final DataServiceReader dataServiceReader;
  private final DataServiceRepository dataServiceRepository;
  private final ApiKeySecretGenerator secrets;
  private final DataServiceRateLimiter rateLimiter;
  private final CurrentProject currentProject;

  public List<ConsumerView> list() {
    int totalApis = dataServiceReader.list().size();
    return consumerRepository.findAll().stream()
        .map(consumer -> view(consumer, totalApis))
        .toList();
  }

  public ConsumerView get(Long consumerId) {
    return view(requireConsumer(consumerId), dataServiceReader.list().size());
  }

  @Transactional
  public ConsumerView create(ConsumerInput input) {
    String name = normalizeName(input == null ? null : input.name());
    if (consumerRepository.existsName(name, null)) {
      throw new IllegalArgumentException("调用方名称已存在：" + name);
    }
    LocalDateTime now = LocalDateTime.now();
    DataServiceConsumer saved = consumerRepository.save(new DataServiceConsumer(
        null,
        currentProject.requireProjectId(),
        name,
        normalizeDescription(input == null ? null : input.description()),
        ConsumerAccessScope.SELECTED,
        input == null || input.enabled() == null || input.enabled(),
        normalizeRate(input == null ? null : input.defaultRateLimitPerMinute()),
        now,
        now));
    return view(saved, dataServiceReader.list().size());
  }

  @Transactional
  public ConsumerView update(Long consumerId, ConsumerInput input) {
    DataServiceConsumer current = requireConsumer(consumerId);
    if (input == null) throw new IllegalArgumentException("调用方配置不能为空");
    String name = StringUtils.hasText(input.name()) ? normalizeName(input.name()) : current.name();
    if (!name.equals(current.name()) && consumerRepository.existsName(name, consumerId)) {
      throw new IllegalArgumentException("调用方名称已存在：" + name);
    }
    DataServiceConsumer saved = consumerRepository.save(new DataServiceConsumer(
        current.id(),
        current.projectId(),
        name,
        input.description() == null ? current.description() : normalizeDescription(input.description()),
        current.accessScope(),
        input.enabled() == null ? current.enabled() : input.enabled(),
        input.defaultRateLimitPerMinute() == null
            ? current.defaultRateLimitPerMinute()
            : normalizeRate(input.defaultRateLimitPerMinute()),
        current.createTime(),
        LocalDateTime.now()));
    return view(saved, dataServiceReader.list().size());
  }

  @Transactional
  public ConsumerView updateAccess(Long consumerId, ConsumerAccessInput input) {
    DataServiceConsumer current = requireConsumer(consumerId);
    if (input == null) throw new IllegalArgumentException("API 权限配置不能为空");
    ConsumerAccessScope nextScope;
    try {
      nextScope = ConsumerAccessScope.parse(input.accessScope());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知 API 权限范围：" + input.accessScope(), exception);
    }

    List<Long> previousIds = coveredApiIds(current);
    List<Long> nextIds = nextScope == ConsumerAccessScope.ALL
        ? dataServiceReader.list().stream().map(DataServiceDefinition::id).toList()
        : normalizeApiIds(input.apiIds());

    // Once an API has been protected by a caller grant, never silently make it public when the
    // grant changes. The legacy API auth flag becomes the fail-closed fallback.
    ensureApiKeyProtection(union(previousIds, nextIds));

    LocalDateTime now = LocalDateTime.now();
    consumerRepository.replaceApiIds(
        consumerId,
        nextScope == ConsumerAccessScope.SELECTED ? nextIds : List.of(),
        now);
    DataServiceConsumer saved = consumerRepository.save(new DataServiceConsumer(
        current.id(), current.projectId(), current.name(), current.description(), nextScope,
        current.enabled(), current.defaultRateLimitPerMinute(), current.createTime(), now));
    return view(saved, dataServiceReader.list().size());
  }

  @Transactional
  public void delete(Long consumerId) {
    DataServiceConsumer consumer = requireConsumer(consumerId);
    ensureApiKeyProtection(coveredApiIds(consumer));
    keyRepository.findByConsumerId(consumerId)
        .forEach(key -> rateLimiter.invalidate(key.id()));
    keyRepository.deleteByConsumerId(consumerId);
    ipAccessRepository.deleteByConsumerId(consumerId);
    consumerRepository.deleteApiGrants(consumerId);
    if (!consumerRepository.delete(consumerId)) {
      throw new IllegalArgumentException("调用方不存在：" + consumerId);
    }
  }

  public List<ApiKeyView> listKeys(Long consumerId) {
    requireConsumer(consumerId);
    return keyRepository.findByConsumerId(consumerId).stream().map(this::keyView).toList();
  }

  @Transactional
  public CreatedApiKey createKey(Long consumerId, ApiKeyInput input) {
    DataServiceConsumer consumer = requireConsumer(consumerId);
    if (input == null || !StringUtils.hasText(input.name())) {
      throw new IllegalArgumentException("Key 名称不能为空");
    }
    String name = input.name().trim();
    if (name.length() > 128) throw new IllegalArgumentException("Key 名称不能超过 128 个字符");
    int rate = input.rateLimitPerMinute() == null
        ? consumer.defaultRateLimitPerMinute()
        : normalizeRate(input.rateLimitPerMinute());
    LocalDateTime expiresAt = normalizeExpiration(input.expiresAt());
    ApiKeySecretGenerator.SecretMaterial secret = secrets.create();
    LocalDateTime now = LocalDateTime.now();
    DataServiceApiKey saved = keyRepository.save(new DataServiceApiKey(
        null, null, consumerId, name, secret.prefix(), secret.hash(), true, rate,
        expiresAt, null, now, now));
    return new CreatedApiKey(keyView(saved), secret.rawKey());
  }

  @Transactional
  public CreatedApiKey rotateKey(Long consumerId, Long keyId) {
    DataServiceApiKey key = requireKey(consumerId, keyId);
    ApiKeySecretGenerator.SecretMaterial secret = secrets.create();
    key.rotate(secret.prefix(), secret.hash(), LocalDateTime.now());
    DataServiceApiKey saved = keyRepository.save(key);
    rateLimiter.invalidate(keyId);
    return new CreatedApiKey(keyView(saved), secret.rawKey());
  }

  @Transactional
  public ApiKeyView updateKey(Long consumerId, Long keyId, ApiKeyUpdate input) {
    DataServiceApiKey key = requireKey(consumerId, keyId);
    if (input == null) throw new IllegalArgumentException("Key 配置不能为空");
    String name = null;
    if (StringUtils.hasText(input.name())) {
      name = input.name().trim();
      if (name.length() > 128) throw new IllegalArgumentException("Key 名称不能超过 128 个字符");
    }
    Integer rate = input.rateLimitPerMinute() == null
        ? null
        : normalizeRate(input.rateLimitPerMinute());
    LocalDateTime expiresAt = input.expiresAtSet()
        ? normalizeExpiration(input.expiresAt())
        : input.expiresAt();
    key.update(name, rate, expiresAt, input.expiresAtSet(), LocalDateTime.now());
    return keyView(keyRepository.save(key));
  }

  @Transactional
  public ApiKeyView setKeyEnabled(Long consumerId, Long keyId, boolean enabled) {
    DataServiceApiKey key = requireKey(consumerId, keyId);
    key.setEnabled(enabled, LocalDateTime.now());
    DataServiceApiKey saved = keyRepository.save(key);
    if (!enabled) rateLimiter.invalidate(keyId);
    return keyView(saved);
  }

  @Transactional
  public void deleteKey(Long consumerId, Long keyId) {
    requireKey(consumerId, keyId);
    if (!keyRepository.delete(keyId)) throw new IllegalArgumentException("API Key 不存在：" + keyId);
    rateLimiter.invalidate(keyId);
  }

  private DataServiceConsumer requireConsumer(Long consumerId) {
    if (consumerId == null) throw new IllegalArgumentException("调用方 ID 不能为空");
    return consumerRepository.findById(consumerId)
        .orElseThrow(() -> new IllegalArgumentException("调用方不存在：" + consumerId));
  }

  private DataServiceApiKey requireKey(Long consumerId, Long keyId) {
    requireConsumer(consumerId);
    if (keyId == null) throw new IllegalArgumentException("API Key ID 不能为空");
    DataServiceApiKey key = keyRepository.findById(keyId)
        .orElseThrow(() -> new IllegalArgumentException("API Key 不存在：" + keyId));
    if (!consumerId.equals(key.consumerId())) {
      throw new IllegalArgumentException("API Key 不存在：" + keyId);
    }
    return key;
  }

  private ConsumerView view(DataServiceConsumer consumer, int totalApis) {
    List<Long> apiIds = consumerRepository.findApiIds(consumer.id());
    List<DataServiceApiKey> keys = keyRepository.findByConsumerId(consumer.id());
    LocalDateTime now = LocalDateTime.now();
    int activeKeys = (int) keys.stream()
        .filter(DataServiceApiKey::enabled)
        .filter(key -> !key.expired(now))
        .count();
    int apiCount = consumer.accessScope() == ConsumerAccessScope.ALL ? totalApis : apiIds.size();
    IpAccessMode ipMode = ipAccessRepository.findMode(consumer.id());
    int ipRules = ipAccessRepository.findRules(consumer.id()).size();
    return new ConsumerView(
        consumer.id(), consumer.name(), consumer.description(), consumer.enabled(),
        consumer.accessScope(), apiIds, apiCount, keys.size(), activeKeys, ipMode, ipRules,
        consumer.defaultRateLimitPerMinute(), consumer.createTime(), consumer.updateTime());
  }

  private List<Long> coveredApiIds(DataServiceConsumer consumer) {
    if (consumer.accessScope() == ConsumerAccessScope.ALL) {
      return dataServiceReader.list().stream().map(DataServiceDefinition::id).toList();
    }
    return consumerRepository.findApiIds(consumer.id());
  }

  private List<Long> normalizeApiIds(List<Long> apiIds) {
    if (apiIds == null || apiIds.isEmpty()) return List.of();
    LinkedHashSet<Long> normalized = new LinkedHashSet<>();
    for (Long apiId : apiIds) {
      if (apiId == null || apiId <= 0L) throw new IllegalArgumentException("API ID 必须大于 0");
      dataServiceReader.require(apiId);
      normalized.add(apiId);
    }
    return List.copyOf(normalized);
  }

  private List<Long> union(List<Long> left, List<Long> right) {
    Set<Long> ids = new LinkedHashSet<>();
    if (left != null) ids.addAll(left);
    if (right != null) ids.addAll(right);
    return List.copyOf(ids);
  }

  private void ensureApiKeyProtection(List<Long> apiIds) {
    if (apiIds == null || apiIds.isEmpty()) return;
    LocalDateTime now = LocalDateTime.now();
    for (Long apiId : apiIds) {
      DataServiceDefinition definition = dataServiceReader.require(apiId);
      if (definition.authMode() == AuthMode.API_KEY) continue;
      definition.setAuthMode(AuthMode.API_KEY, now);
      dataServiceRepository.save(definition);
    }
  }

  private String normalizeName(String value) {
    if (!StringUtils.hasText(value)) throw new IllegalArgumentException("调用方名称不能为空");
    String result = value.trim();
    if (result.length() > 128) throw new IllegalArgumentException("调用方名称不能超过 128 个字符");
    return result;
  }

  private String normalizeDescription(String value) {
    String result = value == null ? null : value.trim();
    if (!StringUtils.hasText(result)) return null;
    if (result.length() > 500) throw new IllegalArgumentException("调用方说明不能超过 500 个字符");
    return result;
  }

  private int normalizeRate(Integer value) {
    int result = value == null ? DEFAULT_RATE_LIMIT : value;
    if (result < 1 || result > MAX_RATE_LIMIT) {
      throw new IllegalArgumentException("每分钟调用限制必须在 1~100000 之间");
    }
    return result;
  }

  private LocalDateTime normalizeExpiration(LocalDateTime value) {
    if (value != null && !value.isAfter(LocalDateTime.now())) {
      throw new IllegalArgumentException("API Key 过期时间必须晚于当前时间");
    }
    return value;
  }

  private ApiKeyView keyView(DataServiceApiKey key) {
    return new ApiKeyView(
        key.id(), key.apiId(), key.name(), key.keyPrefix(), key.enabled(),
        key.rateLimitPerMinute(), key.expiresAt(), key.lastUsedAt(),
        key.createTime(), key.updateTime());
  }
}
