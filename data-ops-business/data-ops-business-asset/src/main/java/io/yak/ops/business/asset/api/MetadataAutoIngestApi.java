package io.yak.ops.business.asset.api;

/**
 * S2: internal Asset Owner port for ONE committed Metadata physical table.
 * The caller must already carry durable project+run+entity evidence.
 * This port NEVER sweeps the provider or infers SOURCE_GONE.
 */
public interface MetadataAutoIngestApi {
  ScopedIngestReceipt ingestCommittedMetadataTable(
      Long projectId, Long metadataEntityId, String operator);

  record ScopedIngestReceipt(Long projectId, Long metadataEntityId,
                             String assetKey, int created, int changed, int restored) {}
}
