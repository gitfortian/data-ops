package io.yak.ops.business.lineage.query;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.repository.LineageRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Reads and validates lineage assets without owning transaction boundaries. */
@Component
public class LineageAssetReader {

  static final int MAX_ASSET_SEARCH_LIMIT = 100;

  private final LineageRepository repository;

  public LineageAssetReader(LineageRepository repository) {
    this.repository = repository;
  }

  public LineageAsset getAsset(long assetId) {
    requirePositive(assetId, "assetId");
    return repository.findAsset(assetId)
        .orElseThrow(() -> new IllegalArgumentException("血缘资产不存在：" + assetId));
  }

  public LineageAsset getAssetByKey(String assetKey) {
    String normalized = required(assetKey, "assetKey", 512);
    return repository.findAssetByKey(normalized)
        .orElseThrow(() -> new IllegalArgumentException("血缘资产不存在：" + normalized));
  }

  public List<LineageAsset> searchAssets(
      String keyword, LineageAssetType assetType, int limit) {
    String normalizedKeyword = optional(keyword, 200);
    int actualLimit = Math.min(MAX_ASSET_SEARCH_LIMIT, Math.max(1, limit));
    return repository.searchAssets(normalizedKeyword, assetType, actualLimit);
  }

  private static long requirePositive(long value, String field) {
    if (value <= 0) throw new IllegalArgumentException(field + " 必须大于 0");
    return value;
  }

  private static String required(String value, String field, int maxLength) {
    String normalized = optional(value, maxLength);
    if (normalized == null) throw new IllegalArgumentException(field + " 不能为空");
    return normalized;
  }

  private static String optional(String value, int maxLength) {
    if (value == null) return null;
    String normalized = value.trim();
    if (normalized.isEmpty()) return null;
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException("字段长度不能超过 " + maxLength);
    }
    return normalized;
  }
}
