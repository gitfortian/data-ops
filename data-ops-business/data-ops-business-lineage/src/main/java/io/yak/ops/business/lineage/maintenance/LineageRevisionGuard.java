package io.yak.ops.business.lineage.maintenance;

import io.yak.ops.business.lineage.repository.LineageRepository;
import org.springframework.stereotype.Component;

/** Serializes publishers and rejects stale revision attempts. */
@Component
public class LineageRevisionGuard {

  private final LineageRepository repository;

  public LineageRevisionGuard(LineageRepository repository) {
    this.repository = repository;
  }

  public boolean lockAndAcceptRevision(String assetKey, int revisionNo) {
    String key = required(assetKey, "assetKey", 512);
    return repository.lockAssetByKey(key)
        .map(
            asset -> {
              if (asset.properties() == null || !asset.properties().has("revisionNo")) {
                return true;
              }
              return asset.properties().path("revisionNo").asInt(Integer.MIN_VALUE)
                  <= revisionNo;
            })
        .orElse(true);
  }

  private static String required(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " 不能为空");
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(field + " 长度不能超过 " + maxLength);
    }
    return normalized;
  }
}
