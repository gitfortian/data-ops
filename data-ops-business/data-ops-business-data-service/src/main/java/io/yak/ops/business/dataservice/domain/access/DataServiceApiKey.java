package io.yak.ops.business.dataservice.domain.access;

import java.time.LocalDateTime;

/** Persisted API-key identity. Raw secret material is intentionally not part of this domain object. */
public final class DataServiceApiKey {
  private final Long id;
  private final Long apiId;
  private final Long consumerId;
  private String name;
  private String keyPrefix;
  private String keyHash;
  private boolean enabled;
  private int rateLimitPerMinute;
  private LocalDateTime expiresAt;
  private LocalDateTime lastUsedAt;
  private final LocalDateTime createTime;
  private LocalDateTime updateTime;

  public DataServiceApiKey(
      Long id, Long apiId, Long consumerId, String name, String keyPrefix, String keyHash,
      boolean enabled, int rateLimitPerMinute, LocalDateTime expiresAt,
      LocalDateTime lastUsedAt, LocalDateTime createTime, LocalDateTime updateTime) {
    this.id = id;
    this.apiId = apiId;
    this.consumerId = consumerId;
    this.name = name;
    this.keyPrefix = keyPrefix;
    this.keyHash = keyHash;
    this.enabled = enabled;
    this.rateLimitPerMinute = rateLimitPerMinute;
    this.expiresAt = expiresAt;
    this.lastUsedAt = lastUsedAt;
    this.createTime = createTime;
    this.updateTime = updateTime;
  }

  /** Compatibility constructor for legacy API-scoped credentials. */
  public DataServiceApiKey(
      Long id, Long apiId, String name, String keyPrefix, String keyHash, boolean enabled,
      int rateLimitPerMinute, LocalDateTime expiresAt, LocalDateTime lastUsedAt,
      LocalDateTime createTime, LocalDateTime updateTime) {
    this(id, apiId, null, name, keyPrefix, keyHash, enabled, rateLimitPerMinute,
        expiresAt, lastUsedAt, createTime, updateTime);
  }

  public void rotate(String prefix, String hash, LocalDateTime now) {
    keyPrefix = prefix;
    keyHash = hash;
    enabled = true;
    lastUsedAt = null;
    updateTime = now;
  }

  public void update(
      String name,
      Integer rateLimit,
      LocalDateTime expiresAt,
      boolean expiresAtSet,
      LocalDateTime now) {
    if (name != null) this.name = name;
    if (rateLimit != null) this.rateLimitPerMinute = rateLimit;
    if (expiresAtSet) this.expiresAt = expiresAt;
    this.updateTime = now;
  }

  public void setEnabled(boolean value, LocalDateTime now) {
    enabled = value;
    updateTime = now;
  }

  public void markUsed(LocalDateTime now) {
    lastUsedAt = now;
    updateTime = now;
  }

  public boolean expired(LocalDateTime now) {
    return expiresAt != null && !expiresAt.isAfter(now);
  }

  public Long id() { return id; }
  public Long apiId() { return apiId; }
  public Long consumerId() { return consumerId; }
  public String name() { return name; }
  public String keyPrefix() { return keyPrefix; }
  public String keyHash() { return keyHash; }
  public boolean enabled() { return enabled; }
  public int rateLimitPerMinute() { return rateLimitPerMinute; }
  public LocalDateTime expiresAt() { return expiresAt; }
  public LocalDateTime lastUsedAt() { return lastUsedAt; }
  public LocalDateTime createTime() { return createTime; }
  public LocalDateTime updateTime() { return updateTime; }
}
