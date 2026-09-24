package io.yak.ops.business.metadata.asset;

import io.yak.ops.business.asset.api.AssetSectionResult;
import io.yak.ops.business.metadata.api.EntityDTO;
import io.yak.ops.business.metadata.api.MetadataQueryApi;
import io.yak.ops.business.metadata.config.ConditionalOnMetadataPersistence;
import io.yak.ops.spi.section.SectionAction;
import io.yak.ops.spi.section.SectionCapability;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionEvidence;
import io.yak.ops.spi.section.SectionProvenance;
import io.yak.ops.spi.section.SectionProvider;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionMapSummary;
import io.yak.ops.spi.section.SectionType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Metadata-owned read adapter for physical-table context in Asset Detail. */
@Component
@ConditionalOnMetadataPersistence
@RequiredArgsConstructor
public class MetadataAssetSectionProvider implements SectionProvider {

  @Override
  public java.util.Set<String> supportedSourceTypes() {
    return java.util.Set.of("METADATA");
  }

  private final MetadataQueryApi metadataQueryApi;

  @Override
  public SectionType sectionType() {
    return SectionType.TECHNICAL_METADATA;
  }

  @Override
  public boolean supports(SectionContext context) {
    return "METADATA".equals(context.sourceType())
        && context.assetKey() != null
        && context.assetKey().startsWith("table:");
  }

  @Override
  public SectionContract query(SectionContext context) {
    EntityDTO table = metadataQueryApi.findPhysicalTable(context.assetKey()).orElse(null);
    if (table == null) {
      return response(context, SectionStatus.EMPTY, Map.of(),
          "Metadata 目录中尚无该物理表记录", List.of());
    }
    List<EntityDTO> columns = metadataQueryApi.listPhysicalColumns(
        table.dataSourceId(), table.databaseName(), table.schemaName(), table.tableName());
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("assetKey", context.assetKey());
    values.put("name", value(table.facts(), "name", context.assetKey()));
    values.put("description", value(table.facts(), "description", ""));
    values.put("databaseName", table.databaseName());
    values.put("schemaName", table.schemaName());
    values.put("tableName", table.tableName());
    values.put("entityStatus", value(table.facts(), "entityStatus", "UNKNOWN"));
    values.put("entityId", table.id());
    values.put("attributes", table.attributes());
    values.put("columns", columns.stream().map(EntityDTO::facts).toList());
    values.put("columnCount", columns.size());
    String target = "/data-asset/catalog?view=entity&entityId=" + table.id()
        + "&assetKey=" + java.net.URLEncoder.encode(
            context.assetKey(), java.nio.charset.StandardCharsets.UTF_8);
    String returnAssetId = context.attributes().get("returnAssetId");
    if (returnAssetId != null && returnAssetId.matches("\\d+")) {
      target += "&returnAssetId=" + returnAssetId;
    }
    return response(context, SectionStatus.OK, values, null, List.of(
        new SectionAction("打开 Metadata 实体", target, context.assetKey())));
  }

  private static SectionContract response(
      SectionContext context,
      SectionStatus status,
      Map<String, Object> values,
      String reason,
      List<SectionAction> actions) {
    Instant observedAt = Instant.now();
    String sourceId = context.sourceId() == null ? context.assetKey() : context.sourceId();
    return new AssetSectionResult(
        SectionType.TECHNICAL_METADATA,
        status,
        "METADATA",
        new SectionMapSummary(values),
        reason,
        null,
        actions,
        status == SectionStatus.EMPTY
            ? List.of() : List.of(new SectionEvidence("METADATA", sourceId, observedAt)),
        new SectionProvenance("METADATA", sourceId, observedAt),
        new SectionCapability(true, true, null));
  }

  private static Object value(Map<String, Object> facts, String key, Object fallback) {
    Object value = facts.get(key);
    return value == null ? fallback : value;
  }
}
