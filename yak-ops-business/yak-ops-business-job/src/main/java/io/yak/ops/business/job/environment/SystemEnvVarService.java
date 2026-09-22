package io.yak.ops.business.job.environment;

import io.yak.ops.business.job.dao.SystemEnvVarDao;
import io.yak.ops.common.bean.po.job.SystemEnvVarPO;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable application facade for application-level environment variables. */
@Service
public class SystemEnvVarService implements TaskEnvironmentResolver {

  private static final Logger log = LoggerFactory.getLogger(SystemEnvVarService.class);

  private final SystemEnvVarDao dao;
  private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

  public SystemEnvVarService(SystemEnvVarDao dao) {
    this.dao = dao;
  }

  @PostConstruct
  void loadFromDatabase() {
    List<SystemEnvVarPO> rows = dao.selectAll();
    for (SystemEnvVarPO row : rows) {
      cache.put(row.getVarKey(), row.getVarValue());
    }
    log.info("Loaded {} application environment variable(s) from database", cache.size());
  }

  public Map<String, String> getAll() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(cache));
  }

  @Override
  public Map<String, String> resolveMergedEnv() {
    Map<String, String> merged = new LinkedHashMap<>(System.getenv());
    merged.putAll(cache);
    return Collections.unmodifiableMap(merged);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void set(String key, String value) {
    String normalizedKey = normalizeKey(key);
    String normalizedValue = value == null ? "" : value;

    SystemEnvVarPO existing = dao.selectByKey(normalizedKey);
    if (existing != null) {
      existing.setVarValue(normalizedValue);
      existing.setUpdateTime(LocalDateTime.now());
      dao.updateByKey(existing);
    } else {
      SystemEnvVarPO po = new SystemEnvVarPO();
      po.setVarKey(normalizedKey);
      po.setVarValue(normalizedValue);
      po.setCreateTime(LocalDateTime.now());
      po.setUpdateTime(LocalDateTime.now());
      dao.insert(po);
    }

    cache.put(normalizedKey, normalizedValue);
    log.info(
        "Application environment variable set: {} = {}",
        normalizedKey,
        maskSensitive(normalizedKey, normalizedValue));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public boolean remove(String key) {
    String normalizedKey = normalizeKey(key);
    int affected = dao.deleteByKey(normalizedKey);
    cache.remove(normalizedKey);
    if (affected > 0) {
      log.info("Application environment variable removed: {}", normalizedKey);
    }
    return affected > 0;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void batchSave(Map<String, String> variables) {
    if (variables == null || variables.isEmpty()) return;
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      set(entry.getKey(), entry.getValue());
    }
  }

  private String normalizeKey(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Environment variable key must not be blank");
    }
    String normalized = key.trim();
    if (normalized.length() > 128) {
      throw new IllegalArgumentException("Environment variable key must not exceed 128 characters");
    }
    if (!normalized.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new IllegalArgumentException(
          "Environment variable key must start with a letter or underscore and contain only "
              + "alphanumeric characters and underscores: " + normalized);
    }
    return normalized;
  }

  private String maskSensitive(String key, String value) {
    String upper = key.toUpperCase();
    if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("TOKEN")
        || upper.contains("KEY") || upper.contains("CREDENTIAL")) {
      return "********";
    }
    return value.length() > 64 ? value.substring(0, 61) + "..." : value;
  }
}
