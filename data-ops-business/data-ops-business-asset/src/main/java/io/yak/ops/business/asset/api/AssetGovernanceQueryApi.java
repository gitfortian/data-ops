package io.yak.ops.business.asset.api;

import io.yak.ops.spi.section.SectionType;
import java.time.LocalDateTime;
import java.util.List;

/** Project-scoped, permission-checked read boundary for governance consumers. */
public interface AssetGovernanceQueryApi {
  List<AssetFact> search(String keyword, int limit);
  AssetFact require(long assetId);
  AssetSectionResult section(long assetId, SectionType sectionType);

  record AssetFact(long id, String assetKey, String sourceType, String sourceId,
      String name, String description, String owner, String status, LocalDateTime updatedAt, String definition) {
    public AssetFact(long id, String assetKey, String sourceType, String sourceId, String name,
        String description, String owner, String status, LocalDateTime updatedAt) {
      this(id, assetKey, sourceType, sourceId, name, description, owner, status, updatedAt, null);
    }
  }
}
