package io.yak.ops.business.asset.application;

import io.yak.ops.business.asset.application.reader.DatasetReader;
import io.yak.ops.business.asset.application.reader.MetadataReader;
import io.yak.ops.business.asset.application.reader.model.DatasetSummary;
import io.yak.ops.business.asset.application.reader.model.MetadataSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Phase7.1 governance extension aggregation entry.
 *
 * Asset owns the user-facing governance view while factual data remains in
 * source domains through reader contracts.
 */
@Service
@RequiredArgsConstructor
public class AssetGovernanceExtensionService {

  private final DatasetReader datasetReader;
  private final MetadataReader metadataReader;

  public AssetGovernanceExtension overview(String assetKey) {
    return new AssetGovernanceExtension(
        datasetReader.read(assetKey),
        metadataReader.read(assetKey));
  }

  public record AssetGovernanceExtension(
      DatasetSummary dataset,
      MetadataSummary metadata) {
  }
}
